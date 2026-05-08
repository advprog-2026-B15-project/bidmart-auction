package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionClosedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final AuctionEventPort auctionEventPort;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeAuction(String auctionId, OffsetDateTime closedAt) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null
                || (auction.getStatus() != AuctionStatus.ACTIVE
                    && auction.getStatus() != AuctionStatus.EXTENDED)) {
            log.debug("Auction {} is not in a closeable state, skipping", auctionId);
            return;
        }

        log.info("Processing closure for auction {}", auctionId);

        Optional<Bid> highestBidOpt = bidRepository.findHighestBid(auctionId);
        Long reservePrice = auction.getReservePrice();

        boolean meetsReservePrice = highestBidOpt.isPresent()
                && (reservePrice == null || highestBidOpt.get().getAmount() >= reservePrice);

        if (meetsReservePrice) {
            closeAsWon(auction, highestBidOpt.get(), closedAt);
        } else {
            closeAsUnsold(auction, closedAt);
        }
    }

    private void closeAsWon(Auction auction, Bid winningBid, OffsetDateTime closedAt) {
        auction.setStatus(AuctionStatus.WON);
        auctionRepository.save(auction);

        List<String> loserUserIds = bidRepository.findDistinctLoserBidderIds(
                auction.getId(), winningBid.getBidderId());

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

        auctionEventPort.publishWinnerDetermined(event);
        log.info("Auction {} closed as WON, published WinnerDeterminedEvent", auction.getId());
    }

    private void closeAsUnsold(Auction auction, OffsetDateTime closedAt) {
        auction.setStatus(AuctionStatus.UNSOLD);
        auctionRepository.save(auction);

        List<String> allBidderIds = bidRepository.findDistinctBidderIdsByAuctionId(auction.getId());

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

        auctionEventPort.publishAuctionClosed(event);
        log.info("Auction {} closed as UNSOLD, published AuctionClosedEvent", auction.getId());
    }
}
