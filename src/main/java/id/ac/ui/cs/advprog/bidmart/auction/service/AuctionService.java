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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.util.List;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionSpecification;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final List<BidValidationStrategy> validationStrategies;
    private final HoldBalancePort holdBalancePort;
    private final AuctionEventPort auctionEventPort;
    private final DistributedLockTemplate lockTemplate;
    private final SseEmitterService sseEmitterService;

    public Page<Auction> findAll(Pageable pageable, AuctionStatus status, Long minPrice, Long maxPrice) {
        return auctionRepository.findAll(AuctionSpecification.filterBy(status, minPrice, maxPrice), pageable);
    }

    @Cacheable(value = "auction", key = "#id")
    public Auction findById(String id) {
        return getAuctionOrThrow(id);
    }

    private Auction getAuctionOrThrow(String id) {
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

    @CacheEvict(value = "auction", key = "#auctionId")
    public Auction activate(String auctionId, String sellerId) {
        Auction auction = getAuctionOrThrow(auctionId);

        if (!auction.getSellerId().equals(sellerId)) {
            throw new IllegalStateException("Only the owner can activate this auction");
        }

        if (auction.getStatus() != AuctionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT auctions can be activated");
        }

        auction.setStatus(AuctionStatus.ACTIVE); 
        return auctionRepository.save(auction);
    }

    @CacheEvict(value = {"auction", "bidHistory"}, key = "#auctionId")
    public Bid placeBid(String auctionId, String bidderId, Long amount) {
        String lockKey = "auction-lock-" + auctionId;
        return lockTemplate.executeWithLock(lockKey, 5, 10, TimeUnit.SECONDS, () -> {
            Auction auction = getAuctionOrThrow(auctionId);

            for (BidValidationStrategy strategy : validationStrategies) {
                strategy.validate(auction, amount);
            }

            String previousBidderId = getPreviousBidderId(auctionId);
            holdBalancePort.holdBalance(bidderId, auctionId, amount);

            handleAntiSniping(auction);
            Bid bid = createAndSaveBid(auction, bidderId, amount);
            publishBidEvents(auction, bid, previousBidderId);

            return bid;
        });
    }

    private String getPreviousBidderId(String auctionId) {
        List<Bid> history = bidRepository.findBidHistory(auctionId);
        return history.isEmpty() ? null : history.get(0).getBidderId();
    }

    private void handleAntiSniping(Auction auction) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (auction.getEndTime() != null && now.plusMinutes(2).isAfter(auction.getEndTime())) {
            auction.setEndTime(auction.getEndTime().plusMinutes(2));
            if (auction.getStatus() == AuctionStatus.ACTIVE) {
                auction.setStatus(AuctionStatus.EXTENDED);
            }
        }
    }

    private Bid createAndSaveBid(Auction auction, String bidderId, Long amount) {
        Bid bid = new Bid();
        bid.setAuction(auction);
        bid.setBidderId(bidderId);
        bid.setAmount(amount);
        bidRepository.save(bid);

        auction.setCurrentPrice(amount);
        auctionRepository.save(auction);
        return bid;
    }

    private void publishBidEvents(Auction auction, Bid bid, String previousBidderId) {
        BidPlacedEvent event = BidPlacedEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType("BidPlaced")
                .eventVersion(1)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .source("bidmart-auction")
                .payload(BidPlacedEvent.Payload.builder()
                        .bidId(bid.getId())
                        .auctionId(auction.getId())
                        .listingId(auction.getListingId())
                        .sellerUserId(auction.getSellerId())
                        .bidderUserId(bid.getBidderId())
                        .previousBidderUserId(previousBidderId)
                        .bidAmount(bid.getAmount())
                        .itemName(auction.getTitle())
                        .build())
                .build();
        auctionEventPort.publishBidPlaced(event);

        java.util.Map<String, Object> broadcastPayload = new java.util.HashMap<>();
        broadcastPayload.put("bidId", bid.getId() != null ? bid.getId() : "");
        broadcastPayload.put("auctionId", auction.getId());
        broadcastPayload.put("bidderId", bid.getBidderId());
        broadcastPayload.put("amount", bid.getAmount());
        broadcastPayload.put("currentPrice", auction.getCurrentPrice() != null ? auction.getCurrentPrice() : 0L);
        broadcastPayload.put("endTime", auction.getEndTime() != null ? auction.getEndTime().toString() : "");
        sseEmitterService.broadcast(auction.getId(), broadcastPayload);
    }

    @Cacheable(value = "bidHistory", key = "#auctionId")
    public List<Bid> getBidHistory(String auctionId) {
        getAuctionOrThrow(auctionId);
        return bidRepository.findBidHistory(auctionId);
    }
}