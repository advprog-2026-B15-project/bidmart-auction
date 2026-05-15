package id.ac.ui.cs.advprog.bidmart.auction.controller;

import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionResponse;
import id.ac.ui.cs.advprog.bidmart.auction.dto.BidResponse;
import id.ac.ui.cs.advprog.bidmart.auction.dto.CreateAuctionRequest;
import id.ac.ui.cs.advprog.bidmart.auction.dto.PlaceBidRequest;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.service.AuctionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.service.SseEmitterService;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
@Tag(name = "Auction", description = "API for Auction Management")
public class AuctionController {

    private final AuctionService auctionService;
    private final SseEmitterService sseEmitterService;

    @GetMapping
    public ResponseEntity<Page<AuctionResponse>> findAll(
            @PageableDefault(size = 10) Pageable pageable,
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice) {
        Page<AuctionResponse> auctions = auctionService.findAll(pageable, status, minPrice, maxPrice)
                .map(AuctionResponse::from);
        return ResponseEntity.ok(auctions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuctionResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(AuctionResponse.from(auctionService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<AuctionResponse> create(
            @Valid @RequestBody CreateAuctionRequest req,
            @RequestAttribute("userId") String sellerId) {
        AuctionResponse res = AuctionResponse.from(auctionService.create(req, sellerId));
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<AuctionResponse> activate(
            @PathVariable String id,
            @RequestAttribute("userId") String sellerId) {
        AuctionResponse res = AuctionResponse.from(auctionService.activate(id, sellerId));
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{id}/bids")
    @Operation(summary = "Place a new bid on an auction", 
        description = "Submit a bid for a specific auction. Validates the amount " +
        "and extends the auction time if placed within the last 2 minutes (Anti-Sniping).")
    public ResponseEntity<BidResponse> placeBid(
            @PathVariable String id,
            @Valid @RequestBody PlaceBidRequest req,
            @RequestAttribute("userId") String bidderId) {
        Bid bid = auctionService.placeBid(id, bidderId, req.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(BidResponse.from(bid));
    }

    @GetMapping("/{id}/bids")
    public ResponseEntity<List<BidResponse>> getBidHistory(@PathVariable String id) {
        List<BidResponse> bids = auctionService.getBidHistory(id)
                .stream()
                .map(BidResponse::from)
                .toList();
        return ResponseEntity.ok(bids);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to live auction updates",
        description = "Returns an SSE stream that pushes bid updates in real-time.")
    public SseEmitter streamAuction(@PathVariable String id) {
        return sseEmitterService.subscribe(id);
    }
}
