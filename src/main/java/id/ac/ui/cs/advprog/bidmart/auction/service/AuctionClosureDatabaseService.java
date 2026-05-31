package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionClosedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.HoldBalancePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionClosureDatabaseService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final HoldBalancePort holdBalancePort;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "auction", key = "#auctionId")
    public void markAsClosed(String auctionId, OffsetDateTime closedAt) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null
                || (auction.getStatus() != AuctionStatus.ACTIVE
                    && auction.getStatus() != AuctionStatus.EXTENDED)) {
            log.debug("Auction {} is not in a closeable state, skipping", auctionId);
            return;
        }

        log.info("Marking auction {} as CLOSED", auctionId);
        auction.setStatus(AuctionStatus.CLOSED);
        auctionRepository.save(auction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "auction", key = "#auctionId")
    public void evaluateClosedAuction(String auctionId, OffsetDateTime evaluatedAt) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.CLOSED) {
            log.debug("Auction {} is not in CLOSED state, skipping evaluation", auctionId);
            return;
        }

        log.info("Processing evaluation for CLOSED auction {}", auctionId);

        Optional<Bid> highestBidOpt = bidRepository.findHighestBid(auctionId);
        Long reservePrice = auction.getReservePrice();

        boolean meetsReservePrice = highestBidOpt.isPresent()
                && (reservePrice == null || highestBidOpt.get().getAmount() >= reservePrice);

        if (meetsReservePrice) {
            closeAsWon(auction, highestBidOpt.get(), evaluatedAt);
        } else {
            closeAsUnsold(auction, evaluatedAt);
        }
    }

    private void closeAsWon(Auction auction, Bid winningBid, OffsetDateTime closedAt) {
        auction.setStatus(AuctionStatus.WON);
        auctionRepository.save(auction);

        List<String> loserUserIds = bidRepository.findDistinctLoserBidderIds(
                auction.getId(), winningBid.getBidderId());

        // Convert winner's held balance to payment
        try {
            holdBalancePort.convertBalance(winningBid.getBidderId(), auction.getId(), winningBid.getAmount());
            log.info("Converted balance for winner={} auction={} amount={}", winningBid.getBidderId(), auction.getId(), winningBid.getAmount());
        } catch (Exception e) {
            log.error("Failed to convert balance for winner={} auction={}: {}", winningBid.getBidderId(), auction.getId(), e.getMessage());
        }

        // Release each loser's held balance
        for (String loserId : loserUserIds) {
            bidRepository.findHighestBidByBidder(auction.getId(), loserId).ifPresent(loserBid -> {
                try {
                    holdBalancePort.releaseBalance(loserId, auction.getId(), loserBid.getAmount());
                    log.info("Released balance for loser={} auction={} amount={}", loserId, auction.getId(), loserBid.getAmount());
                } catch (Exception e) {
                    log.error("Failed to release balance for loser={} auction={}: {}", loserId, auction.getId(), e.getMessage());
                }
            });
        }

        WinnerDeterminedEvent event = WinnerDeterminedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("WinnerDetermined")
                .eventVersion(1)
                .occurredAt(closedAt)
                .source("bidmart-auction")
                .payload(WinnerDeterminedEvent.Payload.builder()
                        .auctionId(auction.getId())
                        .listingId(auction.getListingId())
                        .sellerUserId(auction.getSellerId())
                        .winnerUserId(winningBid.getBidderId())
                        .finalPrice(winningBid.getAmount())
                        .currency("IDR")
                        .itemName(auction.getTitle())
                        .quantity(1)
                        .loserUserIds(loserUserIds)
                        .build())
                .build();

        applicationEventPublisher.publishEvent(new id.ac.ui.cs.advprog.bidmart.auction.service.event.PublishWinnerRabbitEvent(this, event));
        log.info("Auction {} closed as WON, published local event for WinnerDeterminedEvent", auction.getId());
    }

    private void closeAsUnsold(Auction auction, OffsetDateTime closedAt) {
        auction.setStatus(AuctionStatus.UNSOLD);
        auctionRepository.save(auction);

        List<String> allBidderIds = bidRepository.findDistinctBidderIdsByAuctionId(auction.getId());

        // Release all bidders' held balances
        for (String bidderId : allBidderIds) {
            bidRepository.findHighestBidByBidder(auction.getId(), bidderId).ifPresent(bid -> {
                try {
                    holdBalancePort.releaseBalance(bidderId, auction.getId(), bid.getAmount());
                    log.info("Released balance for bidder={} auction={} amount={}", bidderId, auction.getId(), bid.getAmount());
                } catch (Exception e) {
                    log.error("Failed to release balance for bidder={} auction={}: {}", bidderId, auction.getId(), e.getMessage());
                }
            });
        }

        AuctionClosedEvent event = AuctionClosedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("AuctionClosed")
                .eventVersion(1)
                .occurredAt(closedAt)
                .source("bidmart-auction")
                .payload(AuctionClosedEvent.Payload.builder()
                        .auctionId(auction.getId())
                        .listingId(auction.getListingId())
                        .sellerUserId(auction.getSellerId())
                        .closedAt(closedAt)
                        .allBidderIds(allBidderIds)
                        .build())
                .build();

        applicationEventPublisher.publishEvent(new id.ac.ui.cs.advprog.bidmart.auction.service.event.PublishUnsoldRabbitEvent(this, event));
        log.info("Auction {} closed as UNSOLD, published local event for AuctionClosedEvent", auction.getId());
    }
}
