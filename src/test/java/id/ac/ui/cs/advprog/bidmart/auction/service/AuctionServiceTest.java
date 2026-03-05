package id.ac.ui.cs.advprog.bidmart.auction.service;

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

    private Auction expensiveVinyl;

    @BeforeEach
    void setUp() {
        expensiveVinyl = new Auction();
        expensiveVinyl.setId("auction-101");
        expensiveVinyl.setTitle("Signed Arctic Monkeys - AM Vinyl (Limited Edition)");
        expensiveVinyl.setListingId("listing-001");
        expensiveVinyl.setSellerId("seller-001");
        expensiveVinyl.setStartingPrice(15000L);
        expensiveVinyl.setMinimumIncrement(1000L);
        expensiveVinyl.setCurrentBid(0L);
        expensiveVinyl.setStatus(AuctionStatus.DRAFT);
        expensiveVinyl.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
    }

    @Test
    void testCreateAuction() {
        when(auctionRepository.save(any(Auction.class))).thenReturn(expensiveVinyl);

        Auction created = auctionService.create(expensiveVinyl);

        assertNotNull(created);
        assertEquals("Signed Arctic Monkeys - AM Vinyl (Limited Edition)", created.getTitle());
        assertEquals(AuctionStatus.DRAFT, created.getStatus());
        verify(auctionRepository, times(1)).save(any(Auction.class));
    }

    @Test
    void testFindAll() {
        Auction eeaaoProp = new Auction();
        eeaaoProp.setTitle("Original Googly Eye Rock from EEAAO");
        eeaaoProp.setStartingPrice(25000L);
        eeaaoProp.setCurrentBid(0L);

        List<Auction> auctionList = Arrays.asList(expensiveVinyl, eeaaoProp);
        when(auctionRepository.findAll()).thenReturn(auctionList);

        List<Auction> result = auctionService.findAll();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(a -> a.getTitle().contains("EEAAO")));
        verify(auctionRepository, times(1)).findAll();
    }

    @Test
    void testActivateAuctionSuccess() {
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(expensiveVinyl));
        when(auctionRepository.save(any(Auction.class))).thenReturn(expensiveVinyl);

        Auction activated = auctionService.activate("auction-101");

        assertNotNull(activated);
        assertEquals(AuctionStatus.ACTIVE, activated.getStatus());
        verify(auctionRepository, times(1)).findById("auction-101");
        verify(auctionRepository, times(1)).save(expensiveVinyl);
    }

    @Test
    void testActivateAuctionNotFound() {
        when(auctionRepository.findById("invalid-id")).thenReturn(Optional.empty());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            auctionService.activate("invalid-id");
        });

        assertEquals("Auction tidak ditemukan", exception.getMessage());
        verify(auctionRepository, times(1)).findById("invalid-id");
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void testActivateAuctionNotDraft() {
        expensiveVinyl.setStatus(AuctionStatus.ACTIVE);
        when(auctionRepository.findById("auction-101")).thenReturn(Optional.of(expensiveVinyl));

        assertThrows(IllegalStateException.class, () -> {
            auctionService.activate("auction-101");
        });

        verify(auctionRepository, never()).save(any());
    }
}