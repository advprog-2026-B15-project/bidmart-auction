package id.ac.ui.cs.advprog.bidmart.auction.service.cache;

import id.ac.ui.cs.advprog.bidmart.auction.dto.BidResponse;
import id.ac.ui.cs.advprog.bidmart.auction.service.event.LocalBidSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCacheUpdaterListener {

    private final CacheManager cacheManager;

    @EventListener
    public void handleLocalBidSaved(LocalBidSavedEvent event) {
        var auction = event.getAuction();
        var bid = event.getBid();

        log.debug("Observer received local event, updating cache for auction {}", auction.getId());

        var auctionCache = cacheManager.getCache("auction");
        if (auctionCache != null) {
            auctionCache.put(auction.getId(), auction);
        }

        var bidHistoryCache = cacheManager.getCache("bidHistory");
        if (bidHistoryCache != null) {
            List<BidResponse> history = bidHistoryCache.get(auction.getId(), List.class);
            if (history != null) {
                List<BidResponse> updatedHistory = new java.util.ArrayList<>(history);
                updatedHistory.add(0, BidResponse.from(bid));
                bidHistoryCache.put(auction.getId(), updatedHistory);
            }
        }
    }
}
