package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock
    private AuctionRepository auctionRepository;

    @InjectMocks
    private AuctionService auctionService;

    @Test
    void testCreateAuction() {
        Auction auction = new Auction();
        auction.setTitle("Test Item");
        auction.setCurrentBid(100.0);

        when(auctionRepository.save(any(Auction.class))).thenReturn(auction);

        Auction created = auctionService.create("Test Item", 100.0);
        assertEquals("Test Item", created.getTitle());
        assertEquals(100.0, created.getCurrentBid());
    }
}
