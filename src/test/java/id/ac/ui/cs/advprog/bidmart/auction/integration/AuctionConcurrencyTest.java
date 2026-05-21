package id.ac.ui.cs.advprog.bidmart.auction.integration;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.AuctionService;
import id.ac.ui.cs.advprog.bidmart.auction.service.SseEmitterService;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.LockCallback;
import org.redisson.api.RedissonClient;
import org.redisson.api.RMap;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.cache.type=none")
class AuctionConcurrencyTest {

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @MockitoBean
    private SseEmitterService sseEmitterService;

    @MockitoBean
    private AuctionEventPort auctionEventPort;

    @MockitoBean
    private DistributedLockTemplate lockTemplate;

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private CacheManager cacheManager;

    private final ReentrantLock localLock = new ReentrantLock();

    private String auctionId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redissonClient.getMap(anyString())).thenReturn(mock(RMap.class));

        when(lockTemplate.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(LockCallback.class)))
            .thenAnswer(invocation -> {
                localLock.lock();
                try {
                    LockCallback<?> callback = invocation.getArgument(4);
                    return callback.doWithLock();
                } finally {
                    localLock.unlock();
                }
            });

        bidRepository.deleteAll();
        auctionRepository.deleteAll();

        Auction auction = new Auction();
        auction.setTitle("Concurrency Test Auction");
        auction.setStartingPrice(1000L);
        auction.setCurrentPrice(1000L);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setSellerId("seller-1");
        auction.setListingId("listing-concurrency-test");
        auction.setMinimumIncrement(500L);
        auction.setReservePrice(2000L);
        auction.setEndTime(OffsetDateTime.now().plusDays(1));
        auction = auctionRepository.save(auction);
        auctionId = auction.getId();
    }

    @Test
    void testConcurrentBiddingRaceCondition() throws InterruptedException {
        int numberOfThreads = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1); 
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final long bidAmount = 100000L + (i * 10000); 
            executorService.execute(() -> {
                try {
                    latch.await();
                    auctionService.placeBid(auctionId, "user-" + java.util.UUID.randomUUID(), bidAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Bid failed: " + e.getMessage());
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown(); 
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        Auction finalAuction = auctionRepository.findById(auctionId).orElseThrow();
        long bidCount = bidRepository.count();
        
        System.out.println("Success bids: " + successCount.get());
        System.out.println("Failed bids: " + failureCount.get());
        System.out.println("Final Price: " + finalAuction.getCurrentPrice());

        assertEquals(successCount.get(), bidCount, "Jumlah bid di database harus sesuai dengan jumlah eksekusi sukses");
        
        verify(sseEmitterService, timeout(2000).times(successCount.get())).broadcast(eq(auctionId), any());
    }
}
