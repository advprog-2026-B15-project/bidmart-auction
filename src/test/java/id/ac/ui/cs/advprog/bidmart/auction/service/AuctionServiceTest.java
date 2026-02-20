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
        expensiveVinyl.setId(101L);
        expensiveVinyl.setTitle("Signed Arctic Monkeys - AM Vinyl (Limited Edition)");
        expensiveVinyl.setCurrentBid(15000.0);
        expensiveVinyl.setStatus(AuctionStatus.DRAFT);
    }

    @Test
    void testCreateHighEndAuction() {
        when(auctionRepository.save(any(Auction.class))).thenReturn(expensiveVinyl);

        Auction created = auctionService.create("Signed Arctic Monkeys - AM Vinyl (Limited Edition)", 15000.0);

        assertNotNull(created);
        assertEquals("Signed Arctic Monkeys - AM Vinyl (Limited Edition)", created.getTitle());
        assertEquals(15000.0, created.getCurrentBid());
        assertEquals(AuctionStatus.DRAFT, created.getStatus());
        verify(auctionRepository, times(1)).save(any(Auction.class));
    }

    @Test
    void testFindAllExclusiveAuctions() {
        Auction eeaaoProp = new Auction();
        eeaaoProp.setTitle("Original Googly Eye Rock from EEAAO");
        eeaaoProp.setCurrentBid(25000.0);

        List<Auction> luxuryList = Arrays.asList(expensiveVinyl, eeaaoProp);
        when(auctionRepository.findAll()).thenReturn(luxuryList);

        List<Auction> result = auctionService.findAll();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(a -> a.getTitle().contains("EEAAO")));
        verify(auctionRepository, times(1)).findAll();
    }

    @Test
    void testActivateLuxuryAuctionSuccess() {
        when(auctionRepository.findById(101L)).thenReturn(Optional.of(expensiveVinyl));
        when(auctionRepository.save(any(Auction.class))).thenReturn(expensiveVinyl);

        Auction activated = auctionService.activate(101L);

        assertNotNull(activated);
        assertEquals(AuctionStatus.ACTIVE, activated.getStatus());
        verify(auctionRepository, times(1)).findById(101L);
        verify(auctionRepository, times(1)).save(expensiveVinyl);
    }

    @Test
    void testActivateInvalidAuctionId() {
        when(auctionRepository.findById(999L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            auctionService.activate(999L);
        });

        assertEquals("Invalid auction Id:999", exception.getMessage());
        verify(auctionRepository, times(1)).findById(999L);
        verify(auctionRepository, never()).save(any());
    }
}