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
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final List<BidValidationStrategy> validationStrategies;
    private final HoldBalancePort holdBalancePort;
    private final AuctionEventPort auctionEventPort;
    private final DistributedLockTemplate lockTemplate;

    public List<Auction> findAll() {
        return auctionRepository.findAll();
    }

    public Auction findById(String id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Auction not found"));
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
        return lockTemplate.executeWithLock("auction-lock-" + auctionId, 5, 10, java.util.concurrent.TimeUnit.SECONDS, () -> {
            Auction auction = findById(auctionId);

            for (BidValidationStrategy strategy : validationStrategies) {
                strategy.validate(auction, amount);
            }

            String previousBidderId = null;
            List<Bid> history = bidRepository.findBidHistory(auctionId);
            if (!history.isEmpty()) {
                previousBidderId = history.get(0).getBidderId();
            }

            // tahan reservasi saldo dompet penawar baru
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

            auction.setCurrentPrice(amount);
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
                            .listingId(auction.getListingId())
                            .sellerUserId(auction.getSellerId())
                            .bidderUserId(bidderId)
                            .previousBidderUserId(previousBidderId)
                            .bidAmount(amount)
                            .itemName(auction.getTitle())
                            .build())
                    .build();
            auctionEventPort.publishBidPlaced(event); // publish event

            return bid;
        });
    }

    public List<Bid> getBidHistory(String auctionId) {
        findById(auctionId);
        return bidRepository.findBidHistory(auctionId);
    }
}