package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.dto.CreateAuctionRequest;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.HoldBalancePort;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import id.ac.ui.cs.advprog.bidmart.auction.dto.BidPlacedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.service.strategy.BidValidationStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final List<BidValidationStrategy> validationStrategies;
    private final HoldBalancePort holdBalancePort;
    private final AuctionEventPort auctionEventPort;

    public List<Auction> findAll() {
        return auctionRepository.findAll();
    }

    public Auction findById(String id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Auction not found"));
    }

    public Auction create(CreateAuctionRequest req, String sellerId) {
        if (req.getReservePrice() != null
                && req.getReservePrice() <= req.getStartingPrice()) {
            throw new IllegalArgumentException("Reserve price must be greater than starting price");
        }

        Auction auction = new Auction();
        auction.setListingId(req.getListingId());
        auction.setSellerId(sellerId);
        auction.setTitle(req.getTitle());
        auction.setStartingPrice(req.getStartingPrice());
        auction.setReservePrice(req.getReservePrice());
        auction.setMinimumIncrement(req.getMinimumIncrement());
        auction.setEndTime(req.getEndTime());

        return auctionRepository.save(auction);
    }

    public Auction activate(String auctionId, String sellerId) {
        Auction auction = findById(auctionId);

        if (!auction.getSellerId().equals(sellerId)) {
            throw new IllegalStateException("Only the owner can activate this auction");
        }

        if (auction.getStatus() != AuctionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT auctions can be activated");
        }

        auction.setStatus(AuctionStatus.ACTIVE); // DRAFT -> ACTIVE
        return auctionRepository.save(auction);
    }

    public Bid placeBid(String auctionId, String bidderId, Long amount) {
        Auction auction = findById(auctionId);

        for (BidValidationStrategy strategy : validationStrategies) {
            strategy.validate(auction, amount);
        }

        // tahan reservasi saldo dompet via integrasi REST api
        holdBalancePort.holdBalance(bidderId, auctionId, amount);

        // anti-sniping
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (auction.getEndTime() != null && now.plusMinutes(2).isAfter(auction.getEndTime())) {
            auction.setEndTime(auction.getEndTime().plusMinutes(2));
            if (auction.getStatus() == AuctionStatus.ACTIVE) {
                auction.setStatus(AuctionStatus.EXTENDED);
            }
        }

        // simpan state
        Bid bid = new Bid();
        bid.setAuction(auction);
        bid.setBidderId(bidderId);
        bid.setAmount(amount);
        bidRepository.save(bid);

        auction.setCurrentBid(amount);
        auctionRepository.save(auction);

        BidPlacedEvent event = BidPlacedEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType("BidPlaced")
                .eventVersion(1)
                .occurredAt(now)
                .source("bidmart-auction")
                .payload(BidPlacedEvent.Payload.builder()
                        .bidId(bid.getId())
                        .auctionId(auction.getId())
                        .bidderId(bidderId)
                        .amount(amount)
                        .build())
                .build();
        auctionEventPort.publishBidPlaced(event); // publish event

        return bid;
    }

    public List<Bid> getBidHistory(String auctionId) {
        findById(auctionId);
        return bidRepository.findBidHistory(auctionId);
    }
}