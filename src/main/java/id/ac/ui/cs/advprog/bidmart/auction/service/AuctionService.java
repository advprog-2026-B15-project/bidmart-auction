package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.dto.CreateAuctionRequest;
import id.ac.ui.cs.advprog.bidmart.auction.dto.UpdateAuctionRequest;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.HoldBalancePort;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import id.ac.ui.cs.advprog.bidmart.auction.dto.BidPlacedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.dto.BidResponse;
import id.ac.ui.cs.advprog.bidmart.auction.service.strategy.BidValidationStrategy;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;

@Slf4j
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
    private final MeterRegistry meterRegistry;
    private final org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    private Counter bidPlacedCounter;
    private Timer bidLatencyTimer;

    @PostConstruct
    public void initMetrics() {
        bidPlacedCounter = Counter.builder("auction.bids.placed")
                .description("Total number of bids placed successfully")
                .register(meterRegistry);
        bidLatencyTimer = Timer.builder("auction.bid.latency")
                .description("Time taken to place a bid end-to-end")
                .register(meterRegistry);
        io.micrometer.core.instrument.Gauge.builder("auction.active.count", auctionRepository,
                repo -> repo.countByStatusIn(List.of(AuctionStatus.ACTIVE, AuctionStatus.EXTENDED)))
                .description("Number of currently active or extended auctions")
                .register(meterRegistry);
    }

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

    @CacheEvict(value = "auction", key = "#auctionId")
    public Auction update(String auctionId, String sellerId, UpdateAuctionRequest req) {
        Auction auction = getAuctionOrThrow(auctionId);

        if (!auction.getSellerId().equals(sellerId)) {
            throw new IllegalStateException("Only the owner can edit this auction");
        }

        if (auction.getStatus() != AuctionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT auctions can be edited");
        }

        if (req.getTitle() != null) auction.setTitle(req.getTitle());
        if (req.getStartingPrice() != null) auction.setStartingPrice(req.getStartingPrice());
        if (req.getReservePrice() != null) auction.setReservePrice(req.getReservePrice());
        if (req.getMinimumIncrement() != null) auction.setMinimumIncrement(req.getMinimumIncrement());
        if (req.getEndTime() != null) auction.setEndTime(req.getEndTime());

        return auctionRepository.save(auction);
    }

    public Bid placeBid(String auctionId, String bidderId, Long amount) {
        String lockKey = "auction-lock-" + auctionId;
        return bidLatencyTimer.record(() -> {
            Auction preliminaryAuction = getAuctionOrThrow(auctionId);
            validateBid(preliminaryAuction, bidderId, amount);

            holdBalancePort.holdBalance(bidderId, auctionId, amount);

            try {
                Bid result = lockTemplate.executeWithLock(lockKey, 5, 10, TimeUnit.SECONDS, 
                        () -> executeBidUnderLock(auctionId, bidderId, amount));
                bidPlacedCounter.increment();
                return result;
            } catch (Exception e) {
                holdBalancePort.releaseBalance(bidderId, auctionId, amount);
                throw e;
            }
        });
    }

    private void validateBid(Auction auction, String bidderId, Long amount) {
        if (bidderId.equals(auction.getSellerId())) {
            throw new IllegalArgumentException("Seller cannot bid on own auction");
        }
        for (BidValidationStrategy strategy : validationStrategies) {
            strategy.validate(auction, amount);
        }
    }

    private Bid executeBidUnderLock(String auctionId, String bidderId, Long amount) {
        Auction auction = getAuctionOrThrow(auctionId);
        validateBid(auction, bidderId, amount);

        // Capture previous highest bid before saving the new one
        java.util.Optional<Bid> previousHighest = bidRepository.findHighestBid(auctionId);
        String previousBidderId = previousHighest.map(Bid::getBidderId).orElse(null);
        Long previousAmount = previousHighest.map(Bid::getAmount).orElse(null);

        auction.applyAntiSnipingRule();
        Bid bid = createAndSaveBid(auction, bidderId, amount);

        // Release the outbid user's held balance (different bidder only)
        if (previousBidderId != null && !previousBidderId.equals(bidderId) && previousAmount != null) {
            try {
                holdBalancePort.releaseBalance(previousBidderId, auctionId, previousAmount);
            } catch (Exception e) {
                log.warn("Failed to release balance for outbid user={} auction={}: {}",
                        previousBidderId, auctionId, e.getMessage());
            }
        }

        publishBidEvents(auction, bid, previousBidderId);

        return bid;
    }

    private Bid createAndSaveBid(Auction auction, String bidderId, Long amount) {
        Bid bid = new Bid();
        bid.setAuction(auction);
        bid.setBidderId(bidderId);
        bid.setAmount(amount);
        bidRepository.save(bid);

        auction.setCurrentPrice(amount);
        auctionRepository.save(auction);

        applicationEventPublisher.publishEvent(
                new id.ac.ui.cs.advprog.bidmart.auction.service.event.LocalBidSavedEvent(this, auction, bid));

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
    public List<BidResponse> getBidHistory(String auctionId) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new NoSuchElementException("Auction not found");
        }
        return bidRepository.findBidHistory(auctionId).stream()
                .map(BidResponse::from)
                .toList();
    }
}