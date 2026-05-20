package id.ac.ui.cs.advprog.bidmart.auction.service.cache;

import id.ac.ui.cs.advprog.bidmart.auction.dto.BidResponse;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.service.event.LocalBidSavedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionCacheUpdaterListenerTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache auctionCache;

    @Mock
    private Cache bidHistoryCache;

    @InjectMocks
    private AuctionCacheUpdaterListener listener;

    private Auction auction;
    private Bid bid;
    private LocalBidSavedEvent event;

    @BeforeEach
    void setUp() {
        auction = new Auction();
        auction.setId("auction-123");

        bid = new Bid();
        bid.setId("bid-456");
        bid.setBidderId("bidder-1");
        bid.setAmount(1000L);
        bid.setAuction(auction);

        event = new LocalBidSavedEvent(this, auction, bid);
    }

    @Test
    void testHandleLocalBidSaved_WithNullCaches() {
        when(cacheManager.getCache("auction")).thenReturn(null);
        when(cacheManager.getCache("bidHistory")).thenReturn(null);

        listener.handleLocalBidSaved(event);

        verify(cacheManager).getCache("auction");
        verify(cacheManager).getCache("bidHistory");
        verifyNoMoreInteractions(cacheManager);
    }

    @Test
    void testHandleLocalBidSaved_WithEmptyHistory() {
        when(cacheManager.getCache("auction")).thenReturn(auctionCache);
        when(cacheManager.getCache("bidHistory")).thenReturn(bidHistoryCache);
        when(bidHistoryCache.get("auction-123", List.class)).thenReturn(null);

        listener.handleLocalBidSaved(event);

        verify(auctionCache).put("auction-123", auction);
        verify(bidHistoryCache).get("auction-123", List.class);
        verify(bidHistoryCache, never()).put(eq("auction-123"), any());
    }

    @Test
    void testHandleLocalBidSaved_WithExistingHistory() {
        List<BidResponse> existingHistory = new ArrayList<>();
        Bid oldBidEntity = new Bid();
        oldBidEntity.setAmount(500L);
        oldBidEntity.setAuction(auction);
        BidResponse oldBid = BidResponse.from(oldBidEntity);
        existingHistory.add(oldBid);

        when(cacheManager.getCache("auction")).thenReturn(auctionCache);
        when(cacheManager.getCache("bidHistory")).thenReturn(bidHistoryCache);
        when(bidHistoryCache.get("auction-123", List.class)).thenReturn(existingHistory);

        listener.handleLocalBidSaved(event);

        verify(auctionCache).put("auction-123", auction);
        verify(bidHistoryCache).put(eq("auction-123"), argThat(list -> {
            List<BidResponse> result = (List<BidResponse>) list;
            return result.size() == 2 && result.get(0).getAmount().equals(1000L);
        }));
    }
}
