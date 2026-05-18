package id.ac.ui.cs.advprog.bidmart.auction;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @InjectMocks
    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testRunSeederSuccess() {
        when(auctionRepository.save(any(Auction.class))).thenAnswer(i -> i.getArgument(0));

        dataSeeder.run();

        verify(auctionRepository, times(50)).save(any(Auction.class));
    }
}
