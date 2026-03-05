package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @InjectMocks
    private AuctionService auctionService;

    private Auction auction;

    @BeforeEach
    void setUp() {
        auction = new Auction();
        auction.setId("auction-101");
        auction.setSellerId("seller-001");
        auction.setListingId("listing-001");
        auction.setTitle("Vintage Camera");
        auction.setStartingPrice(500000L);
        auction.setMinimumIncrement(50000L);
        auction.setCurrentBid(0L);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
    }

    @Test
    void testPlaceBidSuccessFirstBid() {
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));
        when(bidRepository.save(any(Bid.class))).thenAnswer(i -> i.getArgument(0));
        when(auctionRepository.save(any(Auction.class))).thenReturn(auction);

        Bid result = auctionService.placeBid("auction-101", "bidder-001", 500000L);

        assertNotNull(result);
        assertEquals(500000L, result.getAmount());
        assertEquals("bidder-001", result.getBidderUsername());
        verify(bidRepository, times(1)).save(any(Bid.class));
        verify(auctionRepository, times(1)).save(auction);
    }

    @Test
    void testPlaceBidSuccessSubsequentBid() {
        auction.setCurrentBid(500000L);
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));
        when(bidRepository.save(any(Bid.class))).thenAnswer(i -> i.getArgument(0));
        when(auctionRepository.save(any(Auction.class))).thenReturn(auction);

        Bid result = auctionService.placeBid("auction-101", "bidder-002", 550000L);

        assertNotNull(result);
        assertEquals(550000L, result.getAmount());
    }

    @Test
    void testPlaceBidTooLow() {
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));

        assertThrows(IllegalArgumentException.class, () -> {
            auctionService.placeBid("auction-101", "bidder-001", 100000L);
        });

        verify(bidRepository, never()).save(any());
    }

    @Test
    void testPlaceBidBelowIncrement() {
        auction.setCurrentBid(500000L);
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));

        assertThrows(IllegalArgumentException.class, () -> {
            auctionService.placeBid("auction-101", "bidder-002", 510000L);
        });

        verify(bidRepository, never()).save(any());
    }

    @Test
    void testPlaceBidAuctionNotActive() {
        auction.setStatus(AuctionStatus.DRAFT);
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));

        assertThrows(IllegalStateException.class, () -> {
            auctionService.placeBid("auction-101", "bidder-001", 500000L);
        });

        verify(bidRepository, never()).save(any());
    }

    @Test
    void testPlaceBidAuctionClosed() {
        auction.setStatus(AuctionStatus.CLOSED);
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));

        assertThrows(IllegalStateException.class, () -> {
            auctionService.placeBid("auction-101", "bidder-001", 500000L);
        });

        verify(bidRepository, never()).save(any());
    }

    @Test
    void testGetBidHistory() {
        Bid bid1 = new Bid();
        bid1.setAmount(600000L);
        Bid bid2 = new Bid();
        bid2.setAmount(500000L);

        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));
        when(bidRepository.findBidHistory("auction-101")).thenReturn(Arrays.asList(bid1, bid2));

        List<Bid> result = auctionService.getBidHistory("auction-101");

        assertEquals(2, result.size());
        assertEquals(600000L, result.get(0).getAmount());
    }
}
