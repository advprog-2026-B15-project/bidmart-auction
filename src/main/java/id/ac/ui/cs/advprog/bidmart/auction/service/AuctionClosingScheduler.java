package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionClosedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosingScheduler {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final DistributedLockTemplate lockTemplate;
    private final AuctionEventPort auctionEventPort;

    /**
     * Berjalan secara berkala untuk menutup lelang yang sudah melewati waktu berlakunya (end time).
     * Menggunakan distributed lock untuk memastikan hanya satu instance yang memproses penutupan lelang.
     */
    @Scheduled(fixedRateString = "${bidmart.auction.scheduler.rate:60000}")
    public void closeExpiredAuctions() {
        // Kunci seluruh proses job untuk mencegah bentrok antar instance aplikasi (multi-instance)
        lockTemplate.executeWithLock("auction-scheduler-lock", 0, 30, TimeUnit.SECONDS, () -> {
            log.info("Starting expired auctions closing job");
            
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            List<Auction> expiredAuctions = auctionRepository.findExpiredByStatuses(
                    Arrays.asList(AuctionStatus.ACTIVE, AuctionStatus.EXTENDED), now);

            if (!expiredAuctions.isEmpty()) {
                log.info("Found {} expired auctions to process", expiredAuctions.size());
                // Gunakan parallelStream untuk memproses ribuan lelang secara konkuren
                expiredAuctions.parallelStream().forEach(auction -> {
                    try {
                        processAuctionClosure(auction, now);
                    } catch (Exception e) {
                        log.error("Error processing closure for auction {}: {}", auction.getId(), e.getMessage());
                    }
                });
            }

            log.info("Finished expired auctions closing job, processed {} auctions", expiredAuctions.size());
            return null;
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processAuctionClosure(Auction auction, OffsetDateTime closedAt) {
        // Lock per lelang untuk mencegah bentrok dengan bid yang masuk saat lelang sedang ditutup
        lockTemplate.executeWithLock("auction-lock-" + auction.getId(), 5, 10, TimeUnit.SECONDS, () -> {
            
            // Ambil ulang data dari database untuk memastikan state terbaru
            Auction currentAuction = auctionRepository.findById(auction.getId()).orElse(null);
            if (currentAuction == null || 
                (currentAuction.getStatus() != AuctionStatus.ACTIVE && currentAuction.getStatus() != AuctionStatus.EXTENDED)) {
                return null; // Sudah diproses sebelumnya
            }

            log.info("Processing closure for auction {}", currentAuction.getId());
            
            Optional<Bid> highestBidOpt = bidRepository.findHighestBid(currentAuction.getId());
            Long reservePrice = currentAuction.getReservePrice();
            
            boolean meetsReservePrice = highestBidOpt.isPresent() && 
                    (reservePrice == null || highestBidOpt.get().getAmount() >= reservePrice);

            if (meetsReservePrice) {
                // Skenario ADA PEMENANG (WON)
                Bid winningBid = highestBidOpt.get();
                currentAuction.setStatus(AuctionStatus.WON);
                auctionRepository.save(currentAuction);
                
                List<String> loserUserIds = bidRepository.findDistinctLoserBidderIds(currentAuction.getId(), winningBid.getBidderId());

                WinnerDeterminedEvent event = WinnerDeterminedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("WinnerDetermined")
                        .eventVersion(1)
                        .occurredAt(closedAt)
                        .source("bidmart-auction")
                        .payload(WinnerDeterminedEvent.Payload.builder()
                                .auctionId(currentAuction.getId())
                                .listingId(currentAuction.getListingId())
                                .sellerUserId(currentAuction.getSellerId())
                                .winnerUserId(winningBid.getBidderId())
                                .finalPrice(winningBid.getAmount())
                                .currency("IDR")
                                .itemName(currentAuction.getTitle())
                                .quantity(1) // Asumsi qty 1 untuk lelang standar
                                .loserUserIds(loserUserIds)
                                .build())
                        .build();

                auctionEventPort.publishWinnerDetermined(event);
                log.info("Auction {} closed as WON, published WinnerDeterminedEvent", currentAuction.getId());

            } else {
                // Skenario TIDAK TERJUAL (UNSOLD)
                currentAuction.setStatus(AuctionStatus.UNSOLD);
                auctionRepository.save(currentAuction);

                List<String> allBidderIds = bidRepository.findDistinctBidderIdsByAuctionId(currentAuction.getId());

                AuctionClosedEvent event = AuctionClosedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("AuctionClosed")
                        .eventVersion(1)
                        .occurredAt(closedAt)
                        .source("bidmart-auction")
                        .payload(AuctionClosedEvent.Payload.builder()
                                .auctionId(currentAuction.getId())
                                .listingId(currentAuction.getListingId())
                                .sellerUserId(currentAuction.getSellerId())
                                .closedAt(closedAt)
                                .allBidderIds(allBidderIds)
                                .build())
                        .build();

                auctionEventPort.publishAuctionClosed(event);
                log.info("Auction {} closed as UNSOLD, published AuctionClosedEvent", currentAuction.getId());
            }

            return null;
        });
    }
}
