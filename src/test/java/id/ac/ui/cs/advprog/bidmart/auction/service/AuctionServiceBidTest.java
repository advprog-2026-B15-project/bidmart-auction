package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.dto.BidResponse;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.HoldBalancePort;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.LockCallback;
import id.ac.ui.cs.advprog.bidmart.auction.service.strategy.BidValidationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionServiceBidTest {

    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private BidRepository bidRepository;
    private List<BidValidationStrategy> validationStrategies = new ArrayList<>();
    @Mock
    private HoldBalancePort holdBalancePort;
    @Mock
    private AuctionEventPort auctionEventPort;
    @Mock
    private DistributedLockTemplate lockTemplate;
    @Mock
    private SseEmitterService sseEmitterService;

    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private AuctionService auctionService;

    private Auction auction;

    @BeforeEach
    void setUp() {
        auctionService = new AuctionService(
            auctionRepository,
            bidRepository,
            validationStrategies,
            holdBalancePort,
            auctionEventPort,
            lockTemplate,
            sseEmitterService,
            meterRegistry,
            new org.springframework.cache.concurrent.ConcurrentMapCacheManager("auction", "bidHistory")
        );
        auctionService.initMetrics();
        
        auction = new Auction();
        auction.setId("auction-123");
        auction.setTitle("Test Item");
        auction.setSellerId("seller-1");
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));

        lenient().when(lockTemplate.executeWithLock(any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(invocation -> {
                    LockCallback<?> callback = invocation.getArgument(4);
                    return callback.doWithLock();
                });
    }

    @Test
    void testPlaceBidSuccess() {
        when(auctionRepository.findById("auction-123")).thenReturn(Optional.of(auction));
        when(bidRepository.findHighestBid("auction-123")).thenReturn(Optional.empty());

        Bid result = auctionService.placeBid("auction-123", "bidder-1", 100000L);

        assertNotNull(result);
        assertEquals(100000L, result.getAmount());
        assertEquals("bidder-1", result.getBidderId());
        verify(holdBalancePort).holdBalance("bidder-1", "auction-123", 100000L);
        verify(auctionEventPort).publishBidPlaced(any());
        verify(auctionRepository).save(auction);
    }

    @Test
    void testPlaceBidWithPreviousBidder() {
        Bid oldBid = new Bid();
        oldBid.setBidderId("old-bidder");
        when(auctionRepository.findById("auction-123")).thenReturn(Optional.of(auction));
        when(bidRepository.findHighestBid("auction-123")).thenReturn(Optional.of(oldBid));

        Bid result = auctionService.placeBid("auction-123", "new-bidder", 200000L);

        assertNotNull(result);
        verify(auctionEventPort).publishBidPlaced(argThat(event -> 
            event.getPayload().getPreviousBidderUserId().equals("old-bidder")
        ));
    }

    @Test
    void testPlaceBidAntiSnipingTriggered() {
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1));
        OffsetDateTime originalEnd = auction.getEndTime();

        when(auctionRepository.findById("auction-123")).thenReturn(Optional.of(auction));
        when(bidRepository.findHighestBid("auction-123")).thenReturn(Optional.empty());

        auctionService.placeBid("auction-123", "bidder-1", 150000L);

        assertTrue(auction.getEndTime().isAfter(originalEnd));
        assertEquals(AuctionStatus.EXTENDED, auction.getStatus());
    }

    @Test
    void testPlaceBidAlreadyExtendedStayExtended() {
        auction.setStatus(AuctionStatus.EXTENDED);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1));

        when(auctionRepository.findById("auction-123")).thenReturn(Optional.of(auction));
        when(bidRepository.findHighestBid("auction-123")).thenReturn(Optional.empty());

        auctionService.placeBid("auction-123", "bidder-1", 150000L);

        assertEquals(AuctionStatus.EXTENDED, auction.getStatus());
    }

    @Test
    void testGetBidHistory() {
        when(auctionRepository.existsById("auction-123")).thenReturn(true);
        when(bidRepository.findBidHistory("auction-123")).thenReturn(new ArrayList<>());

        List<BidResponse> result = auctionService.getBidHistory("auction-123");

        assertNotNull(result);
        verify(bidRepository).findBidHistory("auction-123");
    }

    @Test
    void testPlaceBid_sellerCannotBidOnOwnAuction() {
        when(auctionRepository.findById("auction-123")).thenReturn(Optional.of(auction));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auctionService.placeBid("auction-123", "seller-1", 100000L));

        assertEquals("Seller cannot bid on own auction", ex.getMessage());
        verify(holdBalancePort, never()).holdBalance(any(), any(), any());
    }

    @Test
    void testPlaceBid_closedAuctionThrows() {
        auction.setStatus(AuctionStatus.CLOSED);
        validationStrategies.add((a, amount) -> {
            if (a.getStatus() != AuctionStatus.ACTIVE && a.getStatus() != AuctionStatus.EXTENDED) {
                throw new IllegalStateException("Auction is not active");
            }
        });

        when(auctionRepository.findById("auction-123")).thenReturn(Optional.of(auction));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> auctionService.placeBid("auction-123", "bidder-1", 100000L));

        assertEquals("Auction is not active", ex.getMessage());
    }

    @Test
    void testPlaceBid_draftAuctionThrows() {
        auction.setStatus(AuctionStatus.DRAFT);
        validationStrategies.add((a, amount) -> {
            if (a.getStatus() != AuctionStatus.ACTIVE && a.getStatus() != AuctionStatus.EXTENDED) {
                throw new IllegalStateException("Auction is not active");
            }
        });

        when(auctionRepository.findById("auction-123")).thenReturn(Optional.of(auction));

        assertThrows(IllegalStateException.class,
                () -> auctionService.placeBid("auction-123", "bidder-1", 100000L));
    }
}
