package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.LockCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionClosingSchedulerTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private DistributedLockTemplate lockTemplate;

    @Mock
    private AuctionClosureService auctionClosureService;

    private final Executor syncExecutor = Runnable::run;

    private ThreadPoolTaskExecutor realExecutor;

    private AuctionClosingScheduler scheduler;

    private Auction auction1;
    private Auction auction2;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        scheduler = new AuctionClosingScheduler(
                auctionRepository, lockTemplate, auctionClosureService, syncExecutor);

        auction1 = new Auction();
        auction1.setId("auc-1");
        auction1.setStatus(AuctionStatus.ACTIVE);

        auction2 = new Auction();
        auction2.setId("auc-2");
        auction2.setStatus(AuctionStatus.EXTENDED);

        when(lockTemplate.executeWithLock(
                eq("auction-scheduler-lock"), anyLong(), anyLong(), any(TimeUnit.class), any(LockCallback.class)))
            .thenAnswer(inv -> ((LockCallback<Void>) inv.getArgument(4)).doWithLock());
    }

    @AfterEach
    void tearDown() {
        if (realExecutor != null) {
            realExecutor.destroy();
        }
    }

    @Test
    void closeExpiredAuctions_delegatesToClosureServiceForEachAuction() {
        when(auctionRepository.findExpiredByStatuses(anyList(), any(OffsetDateTime.class)))
                .thenReturn(Arrays.asList(auction1, auction2));
        when(auctionRepository.findByStatus(AuctionStatus.CLOSED)).thenReturn(Collections.emptyList());

        scheduler.closeExpiredAuctions();

        verify(auctionClosureService).processMarkAsClosed(eq(auction1), any(OffsetDateTime.class));
        verify(auctionClosureService).processMarkAsClosed(eq(auction2), any(OffsetDateTime.class));
        verifyNoMoreInteractions(auctionClosureService);
    }

    @Test
    void closeExpiredAuctions_noExpiredAuctions_doesNotDelegate() {
        when(auctionRepository.findExpiredByStatuses(anyList(), any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(auctionRepository.findByStatus(AuctionStatus.CLOSED)).thenReturn(Collections.emptyList());

        scheduler.closeExpiredAuctions();

        verifyNoInteractions(auctionClosureService);
    }

    @Test
    void closeExpiredAuctions_closureServiceThrows_continuesProcessingOthers() {
        when(auctionRepository.findExpiredByStatuses(anyList(), any(OffsetDateTime.class)))
                .thenReturn(Arrays.asList(auction1, auction2));
        when(auctionRepository.findByStatus(AuctionStatus.CLOSED)).thenReturn(Collections.emptyList());

        doThrow(new RuntimeException("DB error"))
                .when(auctionClosureService).processMarkAsClosed(eq(auction1), any(OffsetDateTime.class));

        scheduler.closeExpiredAuctions();

        verify(auctionClosureService).processMarkAsClosed(eq(auction2), any(OffsetDateTime.class));
    }

    @Test
    void closeExpiredAuctions_processesAllAuctionsInBatch() {
        Auction auction3 = new Auction();
        auction3.setId("auc-3");
        auction3.setStatus(AuctionStatus.ACTIVE);

        when(auctionRepository.findExpiredByStatuses(anyList(), any(OffsetDateTime.class)))
                .thenReturn(Arrays.asList(auction1, auction2, auction3));
        when(auctionRepository.findByStatus(AuctionStatus.CLOSED)).thenReturn(Collections.emptyList());

        scheduler.closeExpiredAuctions();

        verify(auctionClosureService, times(3))
                .processMarkAsClosed(any(Auction.class), any(OffsetDateTime.class));
    }

    @Test
    void closeExpiredAuctions_processesAuctionsInParallel_notSequentially() throws InterruptedException {
        Auction auction3 = new Auction();
        auction3.setId("auc-3");
        auction3.setStatus(AuctionStatus.ACTIVE);

        List<Auction> threeAuctions = Arrays.asList(auction1, auction2, auction3);
        when(auctionRepository.findExpiredByStatuses(anyList(), any(OffsetDateTime.class)))
                .thenReturn(threeAuctions);
        when(auctionRepository.findByStatus(AuctionStatus.CLOSED)).thenReturn(Collections.emptyList());

        CountDownLatch allStarted = new CountDownLatch(3);
        CountDownLatch proceed = new CountDownLatch(1);
        List<String> threadNames = Collections.synchronizedList(new ArrayList<>());

        doAnswer(inv -> {
            threadNames.add(Thread.currentThread().getName());
            allStarted.countDown(); 
            proceed.await(3, TimeUnit.SECONDS); 
            return null;
        }).when(auctionClosureService).processMarkAsClosed(any(), any());

        realExecutor = buildRealExecutor(3);
        AuctionClosingScheduler parallelScheduler = new AuctionClosingScheduler(
                auctionRepository, lockTemplate, auctionClosureService, realExecutor);

        CompletableFuture<Void> schedulerFuture = CompletableFuture.runAsync(
                parallelScheduler::closeExpiredAuctions);

        boolean allStartedInTime = allStarted.await(2, TimeUnit.SECONDS);
        proceed.countDown();
        schedulerFuture.join();

        assertTrue(allStartedInTime,
                "Semua auction harus diproses secara paralel (bukan sequential). " +
                "Jika timeout, berarti task dijalankan satu per satu.");

        long distinctThreads = threadNames.stream().distinct().count();
        assertTrue(distinctThreads > 1,
                "Harus ada lebih dari 1 thread yang bekerja, tapi hanya ada: " + threadNames);
    }

    @Test
    @SuppressWarnings("unchecked")
    void closeExpiredAuctions_moreThan210Auctions_allProcessedWithoutException() {
        List<Auction> manyAuctions = IntStream.range(0, 300)
                .mapToObj(i -> {
                    Auction a = new Auction();
                    a.setId("auc-" + i);
                    a.setStatus(AuctionStatus.ACTIVE);
                    return a;
                })
                .toList();

        when(auctionRepository.findExpiredByStatuses(anyList(), any(OffsetDateTime.class)))
                .thenReturn(manyAuctions);
        when(auctionRepository.findByStatus(AuctionStatus.CLOSED)).thenReturn(Collections.emptyList());

        AtomicInteger processedCount = new AtomicInteger(0);
        doAnswer(inv -> {
            processedCount.incrementAndGet();
            return null;
        }).when(auctionClosureService).processMarkAsClosed(any(), any());

        realExecutor = buildRealExecutor(10);
        AuctionClosingScheduler stressScheduler = new AuctionClosingScheduler(
                auctionRepository, lockTemplate, auctionClosureService, realExecutor);

        assertDoesNotThrow(stressScheduler::closeExpiredAuctions,
                "CallerRunsPolicy harus mencegah crash saat queue executor penuh");

        assertEquals(300, processedCount.get(),
                "Semua 300 auction harus diproses, CallerRunsPolicy memastikan tidak ada yang terbuang");
    }

    private ThreadPoolTaskExecutor buildRealExecutor(int maxPool) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxPool);
        executor.setMaxPoolSize(maxPool);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("test-closure-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
