package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.dto.CreateAuctionRequest;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
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
class AuctionServiceTest {

    @Mock
    private AuctionRepository auctionRepository;

    @InjectMocks
    private AuctionService auctionService;

    private Auction auction;
    private CreateAuctionRequest request;

    @BeforeEach
    void setUp() {
        auction = new Auction();
        auction.setId("auction-101");
        auction.setListingId("listing-001");
        auction.setSellerId("seller-001");
        auction.setTitle("Vintage Camera");
        auction.setStartingPrice(500000L);
        auction.setMinimumIncrement(50000L);
        auction.setCurrentBid(0L);
        auction.setStatus(AuctionStatus.DRAFT);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));

        request = new CreateAuctionRequest();
        request.setListingId("listing-001");
        request.setTitle("Vintage Camera");
        request.setStartingPrice(500000L);
        request.setMinimumIncrement(50000L);
        request.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
    }

    @Test
    void testCreateAuctionSuccess() {
        when(auctionRepository.save(any(Auction.class))).thenReturn(auction);

        Auction result = auctionService.create(request, "seller-001");

        assertNotNull(result);
        assertEquals("Vintage Camera", result.getTitle());
        assertEquals(AuctionStatus.DRAFT, result.getStatus());
        verify(auctionRepository, times(1)).save(any(Auction.class));
    }

    @Test
    void testCreateAuctionWithReservePriceSuccess() {
        request.setReservePrice(1000000L);
        when(auctionRepository.save(any(Auction.class))).thenReturn(auction);

        Auction result = auctionService.create(request, "seller-001");

        assertNotNull(result);
        verify(auctionRepository, times(1)).save(any(Auction.class));
    }

    @Test
    void testCreateAuctionReservePriceLessThanStartingPrice() {
        request.setReservePrice(100000L);

        assertThrows(IllegalArgumentException.class, () -> {
            auctionService.create(request, "seller-001");
        });

        verify(auctionRepository, never()).save(any());
    }

    @Test
    void testCreateAuctionReservePriceEqualToStartingPrice() {
        request.setReservePrice(500000L);

        assertThrows(IllegalArgumentException.class, () -> {
            auctionService.create(request, "seller-001");
        });

        verify(auctionRepository, never()).save(any());
    }

    // ===== FIND =====

    @Test
    void testFindAllAuctions() {
        Auction auction2 = new Auction();
        auction2.setTitle("Mechanical Keyboard");

        when(auctionRepository.findAll()).thenReturn(Arrays.asList(auction, auction2));

        List<Auction> result = auctionService.findAll();

        assertEquals(2, result.size());
        verify(auctionRepository, times(1)).findAll();
    }

    @Test
    void testFindByIdSuccess() {
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));

        Auction result = auctionService.findById("auction-101");

        assertNotNull(result);
        assertEquals("auction-101", result.getId());
    }

    @Test
    void testFindByIdNotFound() {
        when(auctionRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            auctionService.findById("invalid-id");
        });
    }

    @Test
    void testActivateAuctionSuccess() {
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));
        when(auctionRepository.save(any(Auction.class))).thenReturn(auction);

        Auction result = auctionService.activate("auction-101", "seller-001");

        assertEquals(AuctionStatus.ACTIVE, result.getStatus());
        verify(auctionRepository, times(1)).save(auction);
    }

    @Test
    void testActivateAuctionWrongSeller() {
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));

        assertThrows(IllegalStateException.class, () -> {
            auctionService.activate("auction-101", "seller-999");
        });

        verify(auctionRepository, never()).save(any());
    }

    @Test
    void testActivateAuctionNotDraft() {
        auction.setStatus(AuctionStatus.ACTIVE); // sudah aktif
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(auction));

        assertThrows(IllegalStateException.class, () -> {
            auctionService.activate("auction-101", "seller-001");
        });

        verify(auctionRepository, never()).save(any());
    }

    @Test
    void testActivateAuctionNotFound() {
        when(auctionRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            auctionService.activate("invalid-id", "seller-001");
        });

        verify(auctionRepository, never()).save(any());
    }
}