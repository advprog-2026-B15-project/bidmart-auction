package id.ac.ui.cs.advprog.bidmart.auction.service;

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

    @InjectMocks
    private AuctionService auctionService;

    private Auction auction;

    @BeforeEach
    void setUp() {
        // Gunakan Reflection untuk set field list agar tidak null
        org.springframework.test.util.ReflectionTestUtils.setField(auctionService, "validationStrategies", validationStrategies);
        
        auction = new Auction();
        auction.setId("auction-123");
        auction.setTitle("Test Item");
        auction.setSellerId("seller-1");
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));

        // Mock LockTemplate behavior - gunakan any() untuk semua parameter agar pasti match
        lenient().when(lockTemplate.executeWithLock(any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(invocation -> {
                    LockCallback<?> callback = invocation.getArgument(4);
                    return callback.doWithLock();
                });
    }

    @Test
    void testPlaceBidSuccess() {
        when(auctionRepository.findById("auction-123")).thenReturn(Optional.of(auction));
        when(bidRepository.findBidHistory("auction-123")).thenReturn(Collections.emptyList());

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
        when(bidRepository.findBidHistory("auction-123")).thenReturn(Collections.singletonList(oldBid));

        Bid result = auctionService.placeBid("auction-123", "new-bidder", 200000L);

        assertNotNull(result);
        verify(auctionEventPort).publishBidPlaced(argThat(event -> 
            event.getPayload().getPreviousBidderUserId().equals("old-bidder")
        ));
    }

    @Test
    void testPlaceBidAntiSnipingTriggered() {
        // Set end time to 1 minute from now (within 2 mins)
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1));
        OffsetDateTime originalEnd = auction.getEndTime();

        when(auctionRepository.findById("auction-123")).thenReturn(Optional.of(auction));
        when(bidRepository.findBidHistory("auction-123")).thenReturn(Collections.emptyList());

        auctionService.placeBid("auction-123", "bidder-1", 150000L);

        // Check if end time extended by 2 minutes
        assertTrue(auction.getEndTime().isAfter(originalEnd));
        assertEquals(AuctionStatus.EXTENDED, auction.getStatus());
    }

    @Test
    void testPlaceBidAlreadyExtendedStayExtended() {
        auction.setStatus(AuctionStatus.EXTENDED);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1));

        when(auctionRepository.findById("auction-123")).thenReturn(Optional.of(auction));
        when(bidRepository.findBidHistory("auction-123")).thenReturn(Collections.emptyList());

        auctionService.placeBid("auction-123", "bidder-1", 150000L);

        assertEquals(AuctionStatus.EXTENDED, auction.getStatus());
    }

    @Test
    void testGetBidHistory() {
        when(auctionRepository.findById("auction-123")).thenReturn(Optional.of(auction));
        when(bidRepository.findBidHistory("auction-123")).thenReturn(new ArrayList<>());

        List<Bid> result = auctionService.getBidHistory("auction-123");

        assertNotNull(result);
        verify(bidRepository).findBidHistory("auction-123");
    }
}
