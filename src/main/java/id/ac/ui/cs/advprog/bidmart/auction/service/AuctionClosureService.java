package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionClosureService {

    private final DistributedLockTemplate lockTemplate;
    private final AuctionClosureDatabaseService auctionClosureDatabaseService;

    public void processMarkAsClosed(Auction auction, OffsetDateTime closedAt) {
        lockTemplate.executeWithLock(
                "auction-lock-" + auction.getId(), 5, 10, TimeUnit.SECONDS,
                () -> {
                    auctionClosureDatabaseService.markAsClosed(auction.getId(), closedAt);
                    return null;
                });
    }

    public void processEvaluateClosedAuction(Auction auction, OffsetDateTime evaluatedAt) {
        lockTemplate.executeWithLock(
                "auction-lock-" + auction.getId(), 5, 10, TimeUnit.SECONDS,
                () -> {
                    auctionClosureDatabaseService.evaluateClosedAuction(auction.getId(), evaluatedAt);
                    return null;
                });
    }
}
