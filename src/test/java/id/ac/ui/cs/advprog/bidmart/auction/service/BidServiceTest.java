package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.HoldBalancePort;
import id.ac.ui.cs.advprog.bidmart.auction.service.strategy.AmountValidationStrategy;
import id.ac.ui.cs.advprog.bidmart.auction.service.strategy.BidValidationStrategy;
import id.ac.ui.cs.advprog.bidmart.auction.service.strategy.StatusValidationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
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

    @Mock
    private HoldBalancePort holdBalancePort;

    @Mock
    private AuctionEventPort auctionEventPort;

    @Spy
    private List<BidValidationStrategy> validationStrategies = Arrays.asList(
            new StatusValidationStrategy(),
            new AmountValidationStrategy()
    );

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
        auction.setCurrentPrice(0L);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
    }

    @Test
    void testPlaceBidSuccessFirstBid() {
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));
        when(bidRepository.findBidHistory("auction-101")).thenReturn(Collections.emptyList());
        when(bidRepository.save(any(Bid.class))).thenAnswer(i -> i.getArgument(0));
        when(auctionRepository.save(any(Auction.class))).thenReturn(auction);

        Bid result = auctionService.placeBid("auction-101", "bidder-001", 500000L);

        assertNotNull(result);
        assertEquals(500000L, result.getAmount());
        assertEquals("bidder-001", result.getBidderId());
        // Event harus dipublish sekali
        verify(auctionEventPort, times(1)).publishBidPlaced(any());
        verify(holdBalancePort, times(1)).holdBalance("bidder-001", "auction-101", 500000L);
    }

    @Test
    void testPlaceBidSuccessSubsequentBid() {
        auction.setCurrentPrice(500000L);

        Bid previousBid = new Bid();
        previousBid.setBidderId("bidder-001");
        previousBid.setAmount(500000L);

        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));
        when(bidRepository.findBidHistory("auction-101")).thenReturn(List.of(previousBid));
        when(bidRepository.save(any(Bid.class))).thenAnswer(i -> i.getArgument(0));
        when(auctionRepository.save(any(Auction.class))).thenReturn(auction);

        Bid result = auctionService.placeBid("auction-101", "bidder-002", 550000L);

        assertNotNull(result);
        assertEquals(550000L, result.getAmount());
        verify(auctionEventPort, times(1)).publishBidPlaced(any());
    }

    @Test
    void testPlaceBidTriggersAntiSnipingAndSetsExtended() {
        // Auction berakhir dalam 1 menit (kurang dari 2 menit) -> status jadi EXTENDED
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1));
        auction.setStatus(AuctionStatus.ACTIVE);

        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));
        when(bidRepository.findBidHistory("auction-101")).thenReturn(Collections.emptyList());
        when(bidRepository.save(any(Bid.class))).thenAnswer(i -> i.getArgument(0));
        when(auctionRepository.save(any(Auction.class))).thenReturn(auction);

        auctionService.placeBid("auction-101", "bidder-001", 500000L);

        assertEquals(AuctionStatus.EXTENDED, auction.getStatus());
        verify(auctionRepository, atLeastOnce()).save(auction);
    }

    @Test
    void testPlaceBidAntiSnipingWhenAlreadyExtended() {
        // Saat EXTENDED, anti-sniping hanya memperpanjang waktu tanpa mengubah status
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1));
        auction.setStatus(AuctionStatus.EXTENDED);

        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));
        when(bidRepository.findBidHistory("auction-101")).thenReturn(Collections.emptyList());
        when(bidRepository.save(any(Bid.class))).thenAnswer(i -> i.getArgument(0));
        when(auctionRepository.save(any(Auction.class))).thenReturn(auction);

        auctionService.placeBid("auction-101", "bidder-001", 500000L);

        // Status tetap EXTENDED, tidak berubah lagi
        assertEquals(AuctionStatus.EXTENDED, auction.getStatus());
    }

    @Test
    void testPlaceBidNoAntiSnipingWhenEndTimeNull() {
        auction.setEndTime(null);

        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));
        when(bidRepository.findBidHistory("auction-101")).thenReturn(Collections.emptyList());
        when(bidRepository.save(any(Bid.class))).thenAnswer(i -> i.getArgument(0));
        when(auctionRepository.save(any(Auction.class))).thenReturn(auction);

        Bid result = auctionService.placeBid("auction-101", "bidder-001", 500000L);

        assertNotNull(result);
        // Status tetap ACTIVE karena anti-sniping tidak berjalan
        assertEquals(AuctionStatus.ACTIVE, auction.getStatus());
    }

    @Test
    void testPlaceBidTooLow() {
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));

        assertThrows(IllegalArgumentException.class, () ->
                auctionService.placeBid("auction-101", "bidder-001", 100000L));

        verify(bidRepository, never()).save(any());
        verify(holdBalancePort, never()).holdBalance(any(), any(), any());
    }

    @Test
    void testPlaceBidBelowIncrement() {
        auction.setCurrentPrice(500000L);
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));

        assertThrows(IllegalArgumentException.class, () ->
                auctionService.placeBid("auction-101", "bidder-002", 510000L));

        verify(bidRepository, never()).save(any());
    }

    @Test
    void testPlaceBidAuctionNotActive() {
        auction.setStatus(AuctionStatus.DRAFT);
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));

        assertThrows(IllegalStateException.class, () ->
                auctionService.placeBid("auction-101", "bidder-001", 500000L));

        verify(bidRepository, never()).save(any());
    }

    @Test
    void testPlaceBidAuctionClosed() {
        auction.setStatus(AuctionStatus.CLOSED);
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));

        assertThrows(IllegalStateException.class, () ->
                auctionService.placeBid("auction-101", "bidder-001", 500000L));

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

    @Test
    void testGetBidHistoryAuctionNotFound() {
        when(auctionRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class, () ->
                auctionService.getBidHistory("invalid-id"));
    }
}
