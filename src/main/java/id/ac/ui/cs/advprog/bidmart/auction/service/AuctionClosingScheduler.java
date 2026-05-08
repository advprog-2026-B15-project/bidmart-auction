package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosingScheduler {

    private final AuctionRepository auctionRepository;
    private final DistributedLockTemplate lockTemplate;
    private final AuctionClosureService auctionClosureService;

    @Scheduled(fixedRateString = "${bidmart.auction.scheduler.rate:60000}")
    public void closeExpiredAuctions() {
        lockTemplate.executeWithLock("auction-scheduler-lock", 0, 30, TimeUnit.SECONDS, () -> {
            log.info("Starting expired auctions closing job");

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            List<Auction> expiredAuctions = auctionRepository.findExpiredByStatuses(
                    Arrays.asList(AuctionStatus.ACTIVE, AuctionStatus.EXTENDED), now);

            if (!expiredAuctions.isEmpty()) {
                log.info("Found {} expired auctions to process", expiredAuctions.size());
                expiredAuctions.forEach(auction -> {
                    try {
                        auctionClosureService.processAuctionClosure(auction, now);
                    } catch (Exception e) {
                        log.error("Error processing closure for auction {}: {}", auction.getId(), e.getMessage());
                    }
                });
            }

            log.info("Finished expired auctions closing job, processed {} auctions", expiredAuctions.size());
            return null;
        });
    }
}
