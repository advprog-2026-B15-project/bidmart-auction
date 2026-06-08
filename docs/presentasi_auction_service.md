# BidMart Auction Service — Rangkuman Presentasi

> Dokumen ini mencakup justifikasi, implementasi, dan penjelasan lengkap untuk setiap topik presentasi berdasarkan codebase nyata.

---

## 1. Concurrency — Distributed Lock

### Implementasi Saat Ini

**File**: `src/main/java/.../service/lock/DistributedLockTemplate.java`

```java
public <T> T executeWithLock(
        String lockKey, long waitTime, long leaseTime, TimeUnit unit, LockCallback<T> callback) {
    RLock lock = redissonClient.getLock(lockKey);
    boolean isLocked = false;
    try {
        isLocked = lock.tryLock(waitTime, leaseTime, unit);
        if (!isLocked) {
            throw new IllegalStateException("Could not acquire lock for key: " + lockKey);
        }
        return callback.doWithLock();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Thread interrupted...", e);
    } catch (Exception e) {
        if (e instanceof RuntimeException runtimeException) { // ← ini yang "primitif"
            throw runtimeException;
        }
        throw new RuntimeException("Error executing inside lock...", e);
    } finally {
        if (isLocked && lock.isHeldByCurrentThread()) { // ← double-check ini juga
            lock.unlock();
        }
    }
}
```

**Dipakai di**: `AuctionService.placeBid()` dan `AuctionClosingScheduler`

```java
// AuctionService.java — lock per auction ID, bukan global
String lockKey = "auction-lock-" + auctionId;
Bid result = lockTemplate.executeWithLock(lockKey, 5, 10, TimeUnit.SECONDS,
        () -> executeBidUnderLock(auctionId, bidderId, amount));
```

### Kenapa "Primitif" Menurut Dosen & Justifikasi

| Kritik | Penjelasan | Justifikasi Balik |
|--------|-----------|-------------------|
| `catch (Exception e)` + `instanceof` manual | Cara verbose untuk handle exception dari lambda | `LockCallback` interface bisa dibuat throws checked exception untuk menghindari ini |
| Flag `boolean isLocked` + guard di finally | `synchronized` block Java tidak butuh flag | Dengan `tryLock()`, ini memang dibutuhkan karena acquire bisa gagal |
| Kenapa tidak pakai `synchronized`? | `synchronized` hanya berlaku per-JVM | Kalau 2+ server, `synchronized` tidak cukup — Redis lock menjamin mutual exclusion lintas server |

### Versi yang Lebih Idiomatik (jika harus diperbaiki)

```java
// Opsi perbaikan: LockCallback throws Exception
@FunctionalInterface
public interface LockCallback<T> {
    T doWithLock() throws Exception; // bisa throws checked exception
}

// Maka catch block lebih clean:
public <T> T executeWithLock(...) {
    RLock lock = redissonClient.getLock(lockKey);
    try {
        if (!lock.tryLock(waitTime, leaseTime, unit)) {
            throw new IllegalStateException("Could not acquire lock: " + lockKey);
        }
        return callback.doWithLock();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted waiting for lock", e);
    } finally {
        if (lock.isHeldByCurrentThread()) lock.unlock(); // tidak perlu flag!
    }
}
```

**Kenapa tetap Redis, bukan `synchronized`?**
> *"Sistem ini didesain untuk scalable ke multiple instance. synchronized Java hanya berlaku dalam satu JVM. Kalau ada 2 pod berjalan bersamaan, 2 bid bisa masuk ke database secara bersamaan tanpa Redis Lock. Redis sebagai single source of truth untuk lock menjamin mutual exclusion lintas server — ini adalah trade-off yang disengaja antara network latency vs correctness."*

---

## 2. Asynchronous & Event-Driven

Sistem lelang menggunakan perpaduan **Synchronous** (untuk proses transaksional mutlak) dan **Asynchronous** (untuk notifikasi dan realtime UI).

### Jalur API Wallet (Synchronous REST)
Panggilan ke Wallet Service **TIDAK** lewat RabbitMQ, melainkan lewat REST API call (`WalletRestAdapter`). 
**Justifikasi**: Menahan uang (*hold balance*) adalah validasi krusial. Jika uang tidak cukup, bid **harus digagalkan saat itu juga**. Karenanya proses ini harus *realtime* dan *synchronous* sebelum bid disimpan ke database dan sebelum event RabbitMQ dikirimkan.

### Dual-Channel Architecture

Ketika sebuah bid berhasil, sistem mengirimkan notifikasi lewat **dua jalur berbeda**:

**File**: `src/main/java/.../service/AuctionService.java` (method `publishBidEvents`)

```java
private void publishBidEvents(Auction auction, Bid bid, String previousBidderId) {
    // JALUR 1: RabbitMQ — ASYNC, untuk service lain (wallet, notif email, dll)
    auctionEventPort.publishBidPlaced(event); // → tidak memblokir response

    // JALUR 2: SSE — ASYNC broadcast, untuk update UI browser secara near-realtime
    sseEmitterService.broadcast(auction.getId(), broadcastPayload);
}
```

### Jalur 1: RabbitMQ (Truly Async — Fire & Forget)

**File**: `src/main/java/.../service/adapter/RabbitMQAdapter.java`

```java
@Async("notificationExecutor")  // ← berjalan di thread pool terpisah
@Override
public void publishBidPlaced(BidPlacedEvent event) {
    rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_NAME,
            RabbitMQConfig.ROUTING_KEY_BID_PLACED,
            event
    );
}
```

**Justifikasi `@Async`**: Pengiriman pesan ke RabbitMQ melibatkan network I/O. Kalau ini synchronous, thread bid harus menunggu konfirmasi dari broker sebelum bisa return response ke user. Dengan `@Async`, thread bid langsung selesai dan response dikirim ke user, sementara pengiriman event dilakukan di background thread. Ini mengurangi latensi response bid secara signifikan.

### Jalur 2: SSE (Near-Realtime ke Browser)

**File**: `src/main/java/.../service/SseEmitterService.java`

```java
// ConcurrentHashMap: thread-safe map auctionId → list of connected browsers
private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emittersMap
    = new ConcurrentHashMap<>();

@Async("notificationExecutor")  // broadcast ke banyak client juga async
public void broadcast(String auctionId, Object payload) {
    CopyOnWriteArrayList<SseEmitter> emitters = emittersMap.get(auctionId);
    if (emitters != null) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("BID_UPDATE").data(payload));
            } catch (IOException e) {
                removeEmitter(auctionId, emitter); // auto-cleanup disconnected clients
            }
        }
    }
}
```

**Kenapa `ConcurrentHashMap` + `CopyOnWriteArrayList`?**
- `ConcurrentHashMap`: banyak thread (dari banyak bid) bisa read/write map secara bersamaan tanpa race condition.
- `CopyOnWriteArrayList`: saat iterasi broadcast ke semua client, list tidak berubah walaupun ada client yang disconnect dan dihapus secara concurrent.

### Perbandingan Dua Jalur

| Aspek | RabbitMQ | SSE |
|-------|----------|-----|
| **Target** | Service lain (wallet, email) | Browser user |
| **Realtime?** | Tidak wajib | Ya, harus cepat |
| **Boleh gagal?** | Ada retry di RabbitMQ | Error = client tidak update |
| **Pola** | Event-driven, publish-subscribe | Push ke persistent connection |
| **Thread** | Background (`@Async`) | Background (`@Async`) |

### Observer Pattern via Spring Events

**File**: `src/main/java/.../service/cache/AuctionCacheUpdaterListener.java`

```java
@EventListener
public void handleLocalBidSaved(LocalBidSavedEvent event) {
    // Otomatis update Redis cache saat bid disimpan
    // tanpa AuctionService perlu tahu siapa yang listen
    auctionCache.put(auction.getId(), auction);
    bidHistoryCache.put(auction.getId(), updatedHistory);
}
```

Ini adalah **Observer Pattern** via Spring's `ApplicationEventPublisher`. `AuctionService` publish event, `AuctionCacheUpdaterListener` listen — keduanya tidak saling kenal (loose coupling).

---

## 3. Blue-Green Deployment

### Apa itu & Kenapa Dipakai?
Blue-Green Deployment adalah strategi zero-downtime deployment. Selalu ada dua environment (Blue & Green) yang berjalan di dua AWS EC2 terpisah. Yang satu aktif melayani traffic, yang satu menjadi target deployment berikutnya. Begitu deployment selesai dan diverifikasi, traffic di-switch — tanpa downtime.

**Justifikasi**: Sistem lelang tidak boleh down saat ada lelang aktif. Kalau pakai deploy biasa (stop → deploy → start), ada jeda di mana user tidak bisa bid. Blue-Green menghilangkan jeda ini.

### Implementasi Target Switching di Auction

**File**: `.github/workflows/deploy-blue-green.yml`

```yaml
# CD hanya jalan jika CI sudah sukses
on:
  workflow_run:
    workflows: ["Continuous Integration"]
    types: [completed]
    branches: [main]

jobs:
  deploy-blue-green:
    if: github.event.workflow_run.conclusion == 'success'
    steps:
      # Tentukan target: kalau ACTIVE_ENV=blue, deploy ke green, dan sebaliknya
      - name: Determine Target Environment
        run: |
          if [ "$CURRENT_ACTIVE" == "blue" ]; then
            echo "TARGET_ENV=green" >> $GITHUB_ENV
          else
            echo "TARGET_ENV=blue" >> $GITHUB_ENV
          fi

      # Deploy ke EC2 yang TIDAK aktif (idle)
      - name: Deploy to Target EC2
        run: |
          if [ "${{ env.TARGET_ENV }}" == "blue" ]; then
            docker compose --profile monitoring pull
            docker compose --profile monitoring up -d
          else
            docker compose pull
            docker compose up -d
          fi
```

### Eksekusi Sinyal dari Auction ke Gateway

**File**: `.github/workflows/switch-traffic-gateway.yml`

```yaml
jobs:
  switch-traffic:
    steps:
      # Kirim sinyal ke Gateway repo untuk arahkan traffic
      - name: Beri Sinyal ke Gateway
        uses: peter-evans/repository-dispatch@v3
        with:
          repository: advprog-2026-B15-project/bidmart-gateway
          event-type: switch-to-${{ env.TARGET_ENV }}
```

### Penjelasan Detil Switch Traffic di Gateway

Ketika repository `bidmart-gateway` menerima *dispatch event* ini, pipeline `switch-traffic` di Gateway akan dieksekusi secara otomatis untuk mengubah rute tanpa me-restart server gateway.

**Snippet Eksekusi di Gateway (`switch-traffic.yml` Gateway Workspace)**:
```yaml
      - name: Switch Traffic to GREEN
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.EC2_GATEWAY_HOST }}
          username: ${{ secrets.EC2_GATEWAY_USERNAME }}
          key: ${{ secrets.EC2_GATEWAY_SSH_KEY }}
          script: |
            echo "Waiting for Gateway to be healthy..."
            for i in {1..10}; do curl -s http://127.0.0.1:9090/actuator/health && break || sleep 3; done
            
            echo "Switching traffic to GREEN..."
            # 1. Update Routing URI secara Dinamis
            curl -v --retry 5 -X POST http://127.0.0.1:9090/actuator/gateway/routes/bidmart-auction-dynamic \
              -H "Content-Type: application/json" \
              -d '{"uri": "${{ secrets.AUCTION_SERVICE_URL_GREEN }}", "order": -1, "predicates": [{"name": "Path", "args": {"_genkey_0": "/api/auctions/**"}}], "filters": [{"name": "JwtAuthenticationFilter"}]}'
            
            # 2. Hit endpoint Actuator Refresh
            curl -v --retry 5 -X POST http://127.0.0.1:9090/actuator/gateway/refresh
```

**Penjelasan Detail Skrip Gateway**:
1. **Pengecekan *Health* Bawaan**: Script akan mengecek endpoint `/actuator/health` Gateway untuk memastikan server hidup sebelum dipukul.
2. **Update Rute via API Gateway Actuator**: Melakukan HTTP POST ke `/actuator/gateway/routes/bidmart-auction-dynamic`. Konfigurasi JSON mendefinisikan URI baru (misal: IP EC2 Green), *predicates* (URL path), dan *filters* (JWT auth). Ini membuat *mapping* rute baru tersimpan di Gateway.
3. **HTTP POST `/actuator/gateway/refresh`**: Ini adalah endpoint sakti Spring Cloud Gateway. Saat dieksekusi, Gateway me-reload seluruh *routing rules* ke dalam memori secara **Hot Reload**.
4. **Justifikasi**: Gateway **tidak pernah di-restart**. Proses ini menjamin 100% ketersediaan (Availability). Koneksi dari client (seperti FE yang sedang berinteraksi dengan API Catalog/Wallet) sama sekali tidak akan terputus.

### Alur Lengkap
```
Push ke main
    ↓
CI (test + checkstyle + sonar) — jika GAGAL, CD tidak jalan
    ↓ (hanya jika CI sukses)
CD: deteksi ACTIVE_ENV (misal: blue)
    ↓
Deploy ke GREEN (yang idle) — zero impact ke user
    ↓
Developer QA manual di GREEN
    ↓
Trigger switch-traffic.yml di Repo Auction (manual dispatch, ketik "yes")
    ↓
Gateway terima sinyal → SSH ke Gateway EC2 → Hit POST /routes & /refresh
    ↓
Traffic user otomatis pindah ke GREEN tanpa jeda
ACTIVE_ENV GitHub variabel diupdate → blue
```

### Kasus Menarik untuk Presentasi
- **Rollback instan**: Kalau ada bug di GREEN setelah switch, tinggal trigger switch lagi balik ke BLUE. Tidak perlu deploy ulang.
- **Zero-downtime verifiable**: Monitoring bisa menunjukkan bahwa request count tidak pernah drop ke 0 saat switch dilakukan.

---

## 4. CI/CD Pipeline

### Alur: CD Menunggu CI

**File**: `.github/workflows/deploy-blue-green.yml`

```yaml
on:
  workflow_run:
    workflows: ["Continuous Integration"]  # ← nama harus exact match
    types: [completed]

jobs:
  deploy-blue-green:
    if: github.event.workflow_run.conclusion == 'success'  # ← hanya jalan kalau CI lulus
```

**Justifikasi**: Ini memastikan kode yang cacat (gagal test / gagal checkstyle) tidak pernah sampai ke server production. CD hanya boleh jalan setelah CI membuktikan kualitas kode.

### CI Pipeline Terdiri dari 3 Job Paralel

**File**: `.github/workflows/ci.yml`

```yaml
jobs:
  test:         # Unit test dengan JaCoCo coverage
    run: ./gradlew test

  checkstyle:   # Enforce coding style (Sun/Google style)
    run: ./gradlew checkstyleMain checkstyleTest

  sonar:        # Static analysis, detect bug & code smell
    env:
      SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
    run: ./gradlew test sonar --info
```

**Kenapa 3 job terpisah (paralel)?**
- Ketiga job berjalan **paralel** di GitHub Actions, bukan sequential.
- Kalau checkstyle gagal tapi test lulus, CI tetap fail. Tidak ada yang bisa lolos.
- Waktu total = max(durasi test, checkstyle, sonar) — lebih cepat dari sequential.

### Caching untuk Percepatan CI

```yaml
- name: Cache Gradle packages
  uses: actions/cache@v4
  with:
    path: ~/.gradle/caches
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
```

Gradle dependency di-cache berdasarkan hash dari `build.gradle`. Kalau tidak ada perubahan dependency, CI tidak perlu download ulang — menghemat 1-2 menit per run.

---

## 5. Profiling

Profiling dilakukan menggunakan **IntelliJ IDEA Profiler** untuk mengidentifikasi bottleneck CPU dan Memori.

### A. Memory Optimization (`getPreviousBidderId()`)

**Kode Before (Menarik semua data ke Memory)**:
```java
// Query: SELECT * FROM bids WHERE auction_id = ? ORDER BY amount DESC
// SANGAT BURUK: Menarik ribuan baris bid ke dalam List di memori Java
List<Bid> allBids = bidRepository.findByAuctionIdOrderByAmountDesc(auctionId);
if (allBids.size() > 1) {
    return allBids.get(1).getBidderId(); // Ambil bidder ke-2
}
```

**Kode After (Delegasi `LIMIT 1` ke Database)**:
```java
// Query: SELECT * FROM bids WHERE auction_id = ? ORDER BY amount DESC LIMIT 1 OFFSET 1
// OPTIMAL: Cuma membebani DB untuk ambil 1 baris yang diperlukan
Optional<Bid> secondHighestBid = bidRepository.findSecondHighestBid(auctionId);
return secondHighestBid.map(Bid::getBidderId).orElse(null);
```

- **Justifikasi**: Menarik *seluruh* history bid sebuah lelang (bisa puluhan ribu baris) ke dalam memori Java hanya untuk membuang semuanya dan menyisakan satu objek menyebabkan **Memory Bloat** yang parah (2.67 MB per eksekusi). Dengan query spesifik, kita mendelegasikan beban penyortiran dan pemotongan ke Database engine yang jauh lebih efisien.
- **Hasil Profiling**: 
  - CPU Time turun drastis dari **203 ms** menjadi **19 ms**.
  - Memory Allocation turun drastis dari **2.67 MB** menjadi **387 KB**.

### B. Redis Cache Optimization (`getBidHistory()`)
- **Justifikasi**: Endpoint history sering dipanggil (read-heavy).

**Snippet Implementasi**:
```java
// Before: Selalu memukul DB
// return bidRepository.findByAuctionIdOrderByAmountDesc(auctionId);

// After: Cek Redis dulu, jika kosong baru ke DB (Cache Aside)
List<Bid> history = bidHistoryCache.get(auctionId);
if (history == null) {
    history = bidRepository.findByAuctionIdOrderByAmountDesc(auctionId);
    bidHistoryCache.put(auctionId, history);
}
return history;
```

- **Before (Cold Hit)**: Request langsung memukul database PostgreSQL. CPU Time: **200 ms**.
- **After (Warm Hit)**: Data disajikan langsung dari Redis cache. CPU Time: **0 ms**. Flame graph menunjukkan path eksekusi yang sangat pendek dan bersih, membuktikan beban DB berhasil di-bypass sepenuhnya.

### C. Target Profiling Selanjutnya (Future Work)

**Fungsi: `AuctionClosingScheduler.closeExpiredAuctions()`**

**Snippet Sebelum Optimasi (Rawan Starvation)**:
```java
@Scheduled(cron = "0 * * * * *")
public void closeExpiredAuctions() {
    List<Auction> expiredAuctions = auctionRepository.findExpiredActiveAuctions();
    for (Auction auction : expiredAuctions) {
        // Melempar task I/O berat ke ThreadPool lokal JVM
        taskExecutor.execute(() -> processAuctionClosure(auction));
    }
}
```

**Snippet Rencana Optimasi (Asynchronous Queueing)**:
```java
@Scheduled(cron = "0 * * * * *")
public void closeExpiredAuctions() {
    List<Auction> expiredAuctions = auctionRepository.findExpiredActiveAuctions();
    for (Auction auction : expiredAuctions) {
        // Melempar sekadar pesan ID lelang ke RabbitMQ (Worker node yang akan ambil & proses)
        rabbitTemplate.convertAndSend("auction.exchange", "auction.close", auction.getId());
    }
}
```

- **Justifikasi & Rencana Optimasi**: Fungsi ini dijalankan tiap menit. Saat ini ia mendelegasikan tugas pemrosesan (seperti menarik uang hold dari Wallet pemenang) ke `ThreadPoolTaskExecutor` lokal JVM.
- **Masalah yang Diantisipasi**: Jika API call ke Wallet lambat atau timeout, thread dalam pool tersebut akan tertahan lama (*blocked*). Jika ada 100 lelang ditutup bersamaan dan kapasitas pool maksimal hanya 50, *Thread Starvation* akan terjadi. Pelelangan yang baru habis waktunya akan tertunda proses penutupannya.
- **Langkah Optimasi Mendatang**: 
  1. Melakukan profiling eksekusi *Thread State* untuk melihat proporsi waktu JVM I/O (blocking) dibandingkan waktu eksekusi CPU murni.
  2. Mengubah arsitektur *blocking execute* lokal ini menjadi pola antrian worker eksternal (mengirim ID lelang yang expired ke RabbitMQ/SQS Queue). Dengan demikian, *worker node* terpisah bisa mengambil pesan secara asinkron (*Pub-Sub*) dengan laju yang bisa dikontrol (*Rate Limiting*), tanpa membebani thread utama server yang sedang melayani request lelang.

---

## 6. Monitoring

### Custom Metrics dengan Micrometer

**File**: `src/main/java/.../service/AuctionService.java`

```java
@PostConstruct
public void initMetrics() {
    // Counter: berapa total bid berhasil sejak service start
    bidPlacedCounter = Counter.builder("auction.bids.placed")
            .description("Total number of bids placed successfully")
            .register(meterRegistry);

    // Timer dengan percentile histogram: ukur latensi p50, p95, p99
    bidLatencyTimer = Timer.builder("auction.bid.latency")
            .description("Time taken to place a bid end-to-end")
            .publishPercentileHistogram()   // ← mengaktifkan Apdex & percentile
            .register(meterRegistry);

    // Gauge: real-time count lelang aktif (query live ke DB)
    Gauge.builder("auction.active.count", auctionRepository,
            repo -> repo.countByStatusIn(List.of(AuctionStatus.ACTIVE, AuctionStatus.EXTENDED)))
            .register(meterRegistry);
}
```

**Kenapa `publishPercentileHistogram()`?**
Tanpa ini, hanya ada rata-rata (mean) latensi. Mean bisa menipu — 100 request @ 10ms + 1 request @ 10.000ms = mean 108ms padahal ada 1 user yang sangat sengsara. Dengan percentile histogram:
- **p50** = 50% user mengalami latensi di bawah ini (median)
- **p95** = 95% user mengalami latensi di bawah ini (batas SLA umum)
- **p99** = worst-case yang masih signifikan

### Penjelasan & Target Metrik Lengkap
1. **Apdex Score (Target: Mendekati 1.0, dengan T=0.5s)**
   - Apdex (Application Performance Index) adalah standar industri untuk kepuasan pengguna. Ambang batas toleransi (T) kita adalah **500ms**. Request di bawah 500ms dianggap memuaskan. Metrik ini merangkum seluruh distribusi latensi menjadi satu angka sederhana dari 0 ke 1 untuk dipantau dengan mudah.
2. **Bid Latency Percentiles (p50, p95, p99)**
   - Kita memantau secara ketat **p99**. Jika p99 adalah 2 detik, berarti 1% eksekusi bid paling lambat (worst-case) masih selesai dalam 2 detik. Ini krusial dipantau karena operasi bid kita mengandalkan Redis Distributed Lock yang antriannya bisa memanjang secara dinamis.
3. **Active Auctions (`auction_active_count`)**
   - Menghitung lelang berstatus ACTIVE di database menggunakan Micrometer `Gauge`. Metrik bisnis untuk melihat seberapa besar suplai dan beban sistem saat ini.

### Stack Monitoring

**File**: `docker-compose.yml` + `config/prometheus.yml`

```yaml
# docker-compose.yml — hanya jalan di blue environment (profile monitoring)
services:
  prometheus:
    profiles: ["monitoring"]
    volumes:
      - ./config/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    profiles: ["monitoring"]
    volumes:
      - grafana-storage:/var/lib/grafana  # ← dashboard persisten walau restart
```

```yaml
# config/prometheus.yml — scrape dari kedua environment
scrape_configs:
  - job_name: 'bidmart-auction'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    static_configs:
      - targets: ['host.docker.internal:8083', 'IP_EC2_GREEN:8083']
```

**Alur data**: `Spring Actuator` → `Prometheus` (scrape tiap 10s) → `Grafana` (visualisasi)

### Mengapa Monitoring Hanya di Blue?
Prometheus & Grafana hanya jalan di EC2 Blue (`--profile monitoring`). EC2 Green hanya menjalankan auction service saja, tapi tetap di-scrape oleh Prometheus yang ada di Blue. Ini menghemat resource dan menjaga monitoring tetap available saat traffic switch ke Green.

---

## 7. Software Architecture

### 7.1 Microservices

BidMart terdiri dari beberapa ekosistem service terpisah, masing-masing dengan mesin deployment AWS EC2-nya sendiri:

| Service | Port | Lingkungan (Environment) | Fungsi Utama |
|---------|------|--------------------------|--------------|
| Frontend (Next.js) | 3000 | Vercel (Edge Network) | Menyajikan UI untuk browser user secara global |
| `bidmart-gateway` | 8080 | AWS EC2 (Gateway) | API Gateway, routing, dan filter otentikasi JWT |
| `bidmart-auction` | 8083 | AWS EC2 (Auction Blue/Green) | Pengolahan logika lelang, *bidding*, dan *distributed lock* |
| `bidmart-wallet` | 8080 | AWS EC2 (Wallet) | Pencatatan saldo uang sungguhan, proses *hold balance* |
| `bidmart-booking` | 8085 | AWS EC2 (Booking) | Logika pemesanan dan manajemen pengiriman barang pemenang |
| `bidmart-catalog` | 8080 | AWS EC2 (Catalog) | Katalog produk dasar dan pembuatan *listing* barang |
| `bidmart-auth` | 8081 | AWS EC2 (Auth) | Autentikasi user, registrasi, dan verifikasi kredensial |

**Justifikasi Deployment di AWS EC2 Terpisah**:
1. **Isolasi Kegagalan (Fault Isolation)**: Jika kita menaruh semua service dalam satu instance EC2 raksasa (monolitik deployment) dan instance itu mati mendadak (misal karena *Out of Memory* atau spike traffic di sistem lelang), maka Wallet dan Auth akan ikut mati. Dengan instance EC2 terpisah, kerusakan Auction tidak akan menjatuhkan sistem pengelolaan saldo Wallet.
2. **Skalabilitas Independen**: Auction service menerima lonjakan *traffic* eksponensial di menit-menit menjelang lelang ditutup, sehingga sangat butuh di-scale (contoh: di-deploy dalam Blue-Green dual instance). Di sisi lain, service Catalog mungkin memiliki beban stabil. Pemisahan server EC2 memungkinkan pengalokasian RAM/CPU sesuai beban kerja masing-masing domain tanpa pemborosan.
3. **Security Group & Jaringan Privat (Security)**: Pemisahan EC2 memungkinkan penerapan regulasi firewall virtual (*AWS Security Groups*) yang super ketat. Contoh: Mesin EC2 Wallet **HANYA** diizinkan menerima request HTTP masuk dari IP Privat mesin EC2 Auction dan EC2 Gateway. Akses langsung dari internet luar (IP `0.0.0.0/0`) menuju Wallet **diblokir total** di level infrastruktur jaringan AWS. Pada sistem satu mesin, sekat internal sekuat ini tidak mungkin dilakukan.
4. **Isolasi Proses Deployment**: Tim developer Auction Service dapat melakukan deploy environment baru puluhan kali dalam sehari secara mandiri tanpa harus meminta tim Wallet Service ikut down. Hal ini sangat penting karena Wallet memegang mutasi data uang nyata pengguna.

### 7.2 Komunikasi Antar Microservice

Service berkomunikasi menggunakan 2 pola utama tergantung kebutuhan domain transaksinya:

**A. Synchronous (REST API HTTP via Internal Network)**
- **Kapan dipakai?**: Saat sebuah aksi **wajib** divalidasi keabsahannya sebelum eksekusi berlanjut, dan bersifat *blocking atomic*.
- **Contoh**: Ketika bid ditempatkan, `Auction Service` melakukan call HTTP memanggil `Wallet Service` (via `WalletRestAdapter`) untuk menahan uang (`hold_balance`).
- **Justifikasi**: Jika dompet pengguna kosong, sistem butuh response HTTP seketika agar bid langsung digagalkan dan ditolak kepada user saat itu juga. Karena dikerahkan di mesin EC2 terpisah, call REST HTTP ini mengudara secara internal di subnet VPC AWS yang aman.

**B. Asynchronous (RabbitMQ / Event-Driven Messaging)**
- **Kapan dipakai?**: Saat notifikasi kejadian bersifat *fire-and-forget*, atau untuk memicu proses downstream berat yang sama sekali tidak boleh menahan *response delay* bagi end-user.
- **Contoh**:
```text
[Auction Service]
    ↓ "publishBidPlaced" (Melempar event ke Message Broker)
[Exchange RabbitMQ: bidmart.events]
    ├── [Wallet Service Queue]  ← (Mendengar) Merilis uang yang tertahan milik bidder kalah sebelumnya
    └── [Notification Service]  ← (Mendengar) Mengirimkan email outbid
```
- **Justifikasi**: RabbitMQ bertindak sebagai shock-absorber. User yang baru nge-bid tidak perlu dipaksa menunggu hingga email peringatan terkirim. Broker menampung pesannya.

**Implementasi Hexagonal Architecture (Ports & Adapters) pada Komunikasi Async**

**File**: `src/main/java/.../service/adapter/RabbitMQAdapter.java`

```java
// Port Layer (Core Domain Interface) — Sama sekali tidak tahu menahu soal RabbitMQ
public interface AuctionEventPort {
    void publishBidPlaced(BidPlacedEvent event);
}

// Adapter Layer (Infrastruktur) — Menghubungkan Core ke RabbitMQ
@Component
public class RabbitMQAdapter implements AuctionEventPort {
    @Async("notificationExecutor")
    public void publishBidPlaced(BidPlacedEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY_BID_PLACED, event);
    }
}
```

**Justifikasi Pola Adapter**: Menjamin **Loose Coupling**. Domain logika *bidding* di service hanya perlu memanggil `auctionEventPort.publishBidPlaced(event)`. Kelak jika sistem dire-platform menggunakan Apache Kafka, kita cukup memprogram `KafkaAdapter` tanpa mengubah satu baris pun logika transaksional inti.

### 7.3 API Gateway sebagai Single Entry Point (BFF Pattern)

**File**: `src/main/java/.../config/AuthInterceptor.java`

```java
@Override
public boolean preHandle(HttpServletRequest request, ...) {
    String userId = request.getHeader("X-User-Id");  // ← Di-inject secara aman oleh Gateway setelah lolos cek JWT

    if (userId != null && !userId.trim().isEmpty()) {
        request.setAttribute("userId", userId);  // Siap dipakai controller lokal
        return true;
    }

    if ("GET".equalsIgnoreCase(method)) {
        return true;  // Publik GET tidak memerlukan user
    }

    response.sendError(401, "X-User-Id header is required");
    return false;
}
```

**Justifikasi Interceptor**: Mesin Auction Service **TIDAK** tahu cara membedah JWT token. `bidmart-gateway` (Mesin publik) menanggung beban verifikasi enkripsi JWT. Gateway mendekripsi token, menarik ID penggunanya, dan memasang header `X-User-Id` untuk diteruskan ke internal Auction Service. Konsekuensinya, controller dapat dengan percaya diri langsung memakai parameter ID user tanpa *overhead* sekuriti berulang.

### 7.4 Pola Lanjutan: Choreography & Saga Pattern

Dalam menangani proses transaksi *bidding* yang kompleks, BidMart mengimplementasikan dua pola arsitektur *microservices* tingkat lanjut untuk menghindari *coupling* yang ketat dan *distributed transactions* yang rentan terhadap kegagalan.

**A. Choreography (Event-Driven)**
- **Konsep:** Tidak ada *Orchestrator* terpusat yang mengatur semua layanan. Setiap layanan bereaksi terhadap *event* secara mandiri.
- **Implementasi:** Di `RabbitMQAdapter`, saat sebuah lelang ditutup atau bid terjadi, Auction Service hanya "berteriak" dengan melempar event (misal `BidPlacedEvent`). Ia tidak memanggil API Notifikasi atau API Booking secara langsung. Layanan yang butuh informasi itu (seperti Notifikasi) akan mendengarkan event tersebut dari RabbitMQ dan mengeksekusinya di *background*. Hal ini memastikan performa layanan pelelangan tidak melambat (tetap *Near Real-Time*).

**B. Saga Pattern & Compensating Transactions**
- **Konsep:** *Distributed Transactions* (mem-blokir 2 database berbeda sekaligus) sangat tidak disarankan karena merusak skalabilitas. Solusinya adalah pola **Saga**: memecah operasi panjang menjadi transaksi-transaksi lokal yang berurutan. Jika ada transaksi yang gagal di tengah jalan, sistem memanggil *Compensating Transaction* untuk membatalkan (undo) transaksi yang sudah terlanjur berhasil.
- **Implementasi:** Wujud asli dari *Compensating Transaction* ini terlihat di blok `catch` pada method `placeBid` (`AuctionService.java`):
  1. **Transaksi Lokal 1 (Sukses):** Auction Service memanggil REST API Wallet untuk menahan saldo (`holdBalance`). Uang sukses ditahan di database Wallet.
  2. **Transaksi Lokal 2 (Gagal):** Saat mencoba mencatat bid tersebut ke database Auction (di dalam *Distributed Lock*), terjadi error (misal DB mati atau koneksi putus).
  3. **Kompensasi (Undo):** Sistem otomatis melompat ke blok `catch` dan mengeksekusi `holdBalancePort.releaseBalance(...)`. Operasi ini melepaskan/membatalkan saldo yang sudah terlanjur ditahan di Langkah 1.
  *(Catatan penting untuk pembelaan presentasi: Pemanggilan `releaseBalance` karena ada user yang terkena outbid itu adalah murni logika bisnis reguler, BUKAN compensating transaction).*

---

## Pertanyaan Dosen yang Mungkin Muncul

### Soal Blue-Green
**Q**: Bagaimana kalau switch traffic ke GREEN dan ternyata environmentnya error/buggy?  
**A**: Dapat di-rollback dalam hitungan detik. Cukup masuk GitHub Actions dan eksekusi manual `switch-traffic` ke target BLUE. Lingkungan Blue sebelumnya masih menyala sempurna sehingga perpindahan arah rute di Gateway cukup menyegarkan *traffic state* tanpa proses *deploy* ulang (Zero Data Loss).

### Soal Concurrency
**Q**: Kenapa tidak pakai blok kode `synchronized` biasa saja di Java?  
**A**: Kata kunci `synchronized` di Java hanya berlaku mutual di dalam satu JVM proses tunggal. Aplikasi ini didesain beroperasi secara klasterisasi *horizontal scalable* multi-mesin (Contoh ada Instance Blue dan Green). Jika terdapat 2 request paralel masuk menyebar di server EC2 yang berbeda, database akan terinfiltrasi bid ganda. Redis berbasis *Distributed Lock* menjadi pusat konfirmasi *truth lock* absolut di penjuru ekosistem.

### Soal Async & SSE
**Q**: Bukankah notifikasi `SSE` yang dijuluki *realtime* itu seharusnya `synchronous`?  
**A**: Secara eksekusi jaringan antara node browser ke node server, ya, koneksinya ditahan terbuka. Namun secara program server JVM (`@Async broadcast()`), operasinya asinkron untuk menjaga agar siklus thread yang sedang mengamankan uang dan data *bidder* tidak terjeda antri menunggu putaran loop HTTP transmisi SSE ke beribu browser penonton pasif. Respon ke *bidder* mutlak nomor satu kecepatannya.

### Soal CI/CD
**Q**: Mengapa Continuous Deployment (CD) sengaja diputus pipeline-nya agar menunggu CI selesai?  
**A**: Kebijakan rilis bersih (Gatekeeping). Kode yang merusak logic (Unit Test gagal), menyalahi aturan tata bahasa (Checkstyle), atau mempunyai celah resiko kode (SonarCloud) secara teknikal pantang mencemari Production. Memisahkan *runner pipeline* juga menghemat pemakaian resource server apabila tahap tes linting sudah gagal duluan.

### Soal Monitoring
**Q**: Mengapa metrik "Total Bids Placed" di Grafana nilainya teramat rendah sewaktu kamu mendemokan awal?  
**A**: Profiling skrip simulator (*Traffic Generator*) yang terdahulu diotaki satu akun pengguna. Menghujan API berulang-ulang dengan identitas tunggal tentu saja terbentur restriksi *"Anda sudah memegang bid tertinggi"*. Skrip kami mutakhirkan untuk memainkan ping-pong persaingan algoritmis antar 2 user sehingga lonjakan angka transaksi bid memuncak sesuai skenario dunia nyata.
