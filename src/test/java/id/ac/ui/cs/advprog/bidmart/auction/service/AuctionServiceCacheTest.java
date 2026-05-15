package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.config.RedisCacheConfig;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.HoldBalancePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import id.ac.ui.cs.advprog.bidmart.auction.service.strategy.BidValidationStrategy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.LockCallback;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {
    AuctionService.class, 
    AuctionServiceCacheTest.TestCacheConfig.class
})
@TestPropertySource(properties = {"spring.main.allow-bean-definition-overriding=true"})
class AuctionServiceCacheTest {

    @TestConfiguration
    @org.springframework.cache.annotation.EnableCaching
    static class TestCacheConfig {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("auction", "bidHistory");
        }
    }

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private AuctionRepository auctionRepository;

    @MockitoBean
    private BidRepository bidRepository;

    @MockitoBean
    private BidValidationStrategy bidValidationStrategy;

    @MockitoBean
    private HoldBalancePort holdBalancePort;

    @MockitoBean
    private AuctionEventPort auctionEventPort;

    @MockitoBean
    private DistributedLockTemplate lockTemplate;

    @MockitoBean
    private JwtService jwtService;

    private Auction auction;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("auction").clear();
        cacheManager.getCache("bidHistory").clear();

        auction = new Auction();
        auction.setId("auc-123");
        auction.setTitle("Test Cache");

        when(auctionRepository.findById("auc-123")).thenReturn(Optional.of(auction));
        
        when(lockTemplate.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any()))
            .thenAnswer(invocation -> {
                LockCallback<?> callback = invocation.getArgument(4);
                return callback.doWithLock();
            });
    }

    @Test
    void testFindByIdUsesCache() {
        auctionService.findById("auc-123");
        verify(auctionRepository, times(1)).findById("auc-123");

        auctionService.findById("auc-123");
        verify(auctionRepository, times(1)).findById("auc-123");
    }

    @Test
    void testActivateEvictsCache() {
        auctionService.findById("auc-123");
        verify(auctionRepository, times(1)).findById("auc-123");

        auction.setStatus(id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus.DRAFT);
        auction.setSellerId("seller-123");
        auctionService.activate("auc-123", "seller-123");

        auctionService.findById("auc-123");

        verify(auctionRepository, times(3)).findById("auc-123");
    }

    @Test
    void testPlaceBidEvictsCaches() throws Exception {
        auctionService.findById("auc-123");
        auctionService.getBidHistory("auc-123");

        auction.setStatus(id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus.ACTIVE);
        when(auctionRepository.save(any(Auction.class))).thenReturn(auction);
        auctionService.placeBid("auc-123", "buyer-1", 1000L);

        auctionService.findById("auc-123");
        auctionService.getBidHistory("auc-123");

        verify(auctionRepository, times(5)).findById("auc-123");

        verify(bidRepository, times(3)).findBidHistory("auc-123");
    }
}
