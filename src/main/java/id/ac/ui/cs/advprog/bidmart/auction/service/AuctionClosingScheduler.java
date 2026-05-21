package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AuctionClosingScheduler {

    private final AuctionRepository auctionRepository;
    private final DistributedLockTemplate lockTemplate;
    private final AuctionClosureService auctionClosureService;
    private final Executor auctionClosureExecutor;

    public AuctionClosingScheduler(
            AuctionRepository auctionRepository,
            DistributedLockTemplate lockTemplate,
            AuctionClosureService auctionClosureService,
            @Qualifier("auctionClosureExecutor") Executor auctionClosureExecutor) {
        this.auctionRepository = auctionRepository;
        this.lockTemplate = lockTemplate;
        this.auctionClosureService = auctionClosureService;
        this.auctionClosureExecutor = auctionClosureExecutor;
    }

    @Scheduled(fixedRateString = "${bidmart.auction.scheduler.rate:60000}")
    public void closeExpiredAuctions() {
        lockTemplate.executeWithLock("auction-scheduler-lock", 0, -1, TimeUnit.SECONDS, () -> {
            log.info("Starting expired auctions closing job (Phase 1: Mark as CLOSED)");

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            List<Auction> expiredAuctions = auctionRepository.findExpiredByStatuses(
                    Arrays.asList(AuctionStatus.ACTIVE, AuctionStatus.EXTENDED), now);

            if (!expiredAuctions.isEmpty()) {
                log.info("Found {} expired auctions to mark as CLOSED", expiredAuctions.size());

                List<CompletableFuture<Void>> futures = expiredAuctions.stream()
                        .map(auction -> CompletableFuture.runAsync(
                                () -> processMarkAsClosed(auction, now),
                                auctionClosureExecutor))
                        .toList();

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            log.info("Starting closed auctions evaluation job (Phase 2: Evaluate CLOSED)");

            List<Auction> closedAuctions = auctionRepository.findByStatus(AuctionStatus.CLOSED);

            if (!closedAuctions.isEmpty()) {
                log.info("Found {} CLOSED auctions to evaluate", closedAuctions.size());

                List<CompletableFuture<Void>> futures = closedAuctions.stream()
                        .map(auction -> CompletableFuture.runAsync(
                                () -> processEvaluateClosedAuction(auction, now),
                                auctionClosureExecutor))
                        .toList();

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            log.info("Finished auctions closing job");
            return null;
        });
    }

    private void processMarkAsClosed(Auction auction, OffsetDateTime now) {
        try {
            auctionClosureService.processMarkAsClosed(auction, now);
        } catch (Exception e) {
            log.error("Error marking auction {} as CLOSED: {}", auction.getId(), e.getMessage(), e);
        }
    }

    private void processEvaluateClosedAuction(Auction auction, OffsetDateTime now) {
        try {
            auctionClosureService.processEvaluateClosedAuction(auction, now);
        } catch (Exception e) {
            log.error("Error evaluating CLOSED auction {}: {}", auction.getId(), e.getMessage(), e);
        }
    }
}
