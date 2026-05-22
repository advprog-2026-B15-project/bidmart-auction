package id.ac.ui.cs.advprog.bidmart.auction.controller;

import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionResponse;
import id.ac.ui.cs.advprog.bidmart.auction.dto.BidResponse;
import id.ac.ui.cs.advprog.bidmart.auction.dto.CreateAuctionRequest;
import id.ac.ui.cs.advprog.bidmart.auction.dto.PlaceBidRequest;
import id.ac.ui.cs.advprog.bidmart.auction.dto.UpdateAuctionRequest;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.service.AuctionService;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.service.SseEmitterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
@Tag(name = "Auction", description = "API for managing auctions: create, activate, bid, and stream.")
public class AuctionController {

    private final AuctionService auctionService;
    private final SseEmitterService sseEmitterService;

    @GetMapping
    @Operation(
        summary = "List all auctions",
        description = "Returns a paginated list of auctions. Supports filtering by status and price range."
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved auction list")
    public ResponseEntity<Page<AuctionResponse>> findAll(
            @PageableDefault(size = 10) Pageable pageable,
            @Parameter(description = "Filter by auction status (DRAFT, ACTIVE, EXTENDED, CLOSED)")
            @RequestParam(required = false) AuctionStatus status,
            @Parameter(description = "Minimum current price filter")
            @RequestParam(required = false) Long minPrice,
            @Parameter(description = "Maximum current price filter")
            @RequestParam(required = false) Long maxPrice) {
        Page<AuctionResponse> auctions = auctionService.findAll(pageable, status, minPrice, maxPrice)
                .map(AuctionResponse::from);
        return ResponseEntity.ok(auctions);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get auction by ID", description = "Fetches the full detail of a specific auction.")
    @ApiResponse(responseCode = "200", description = "Auction found")
    @ApiResponse(responseCode = "404", description = "Auction not found")
    public ResponseEntity<AuctionResponse> findById(
            @Parameter(description = "Auction ID (UUID)", required = true)
            @PathVariable String id) {
        return ResponseEntity.ok(AuctionResponse.from(auctionService.findById(id)));
    }

    @PostMapping
    @Operation(
        summary = "Create a new auction",
        description = "Creates an auction in DRAFT state. Seller identity is taken from the X-User-Id header (injected by API Gateway)."
    )
    @ApiResponse(responseCode = "201", description = "Auction created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid X-User-Id header")
    public ResponseEntity<AuctionResponse> create(
            @Valid @RequestBody CreateAuctionRequest req,
            @Parameter(hidden = true) @RequestAttribute("userId") String sellerId) {
        AuctionResponse res = AuctionResponse.from(auctionService.create(req, sellerId));
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PatchMapping("/{id}")
    @Operation(
        summary = "Update a draft auction",
        description = "Allows the seller to update editable fields (title, prices, endTime, minimumIncrement) while the auction is still in DRAFT status. Only the auction owner can perform this action."
    )
    @ApiResponse(responseCode = "200", description = "Auction updated successfully")
    @ApiResponse(responseCode = "400", description = "Auction is not in DRAFT state")
    @ApiResponse(responseCode = "403", description = "Caller is not the auction owner")
    @ApiResponse(responseCode = "404", description = "Auction not found")
    public ResponseEntity<AuctionResponse> update(
            @Parameter(description = "Auction ID", required = true) @PathVariable String id,
            @Valid @RequestBody UpdateAuctionRequest req,
            @Parameter(hidden = true) @RequestAttribute("userId") String sellerId) {
        AuctionResponse res = AuctionResponse.from(auctionService.update(id, sellerId, req));
        return ResponseEntity.ok(res);
    }

    @PatchMapping("/{id}/activate")
    @Operation(
        summary = "Activate a draft auction",
        description = "Transitions an auction from DRAFT to ACTIVE. Only the seller who created it can activate it."
    )
    @ApiResponse(responseCode = "200", description = "Auction activated successfully")
    @ApiResponse(responseCode = "400", description = "Auction is not in DRAFT state")
    @ApiResponse(responseCode = "403", description = "Caller is not the auction owner")
    @ApiResponse(responseCode = "404", description = "Auction not found")
    public ResponseEntity<AuctionResponse> activate(
            @Parameter(description = "Auction ID", required = true) @PathVariable String id,
            @Parameter(hidden = true) @RequestAttribute("userId") String sellerId) {
        AuctionResponse res = AuctionResponse.from(auctionService.activate(id, sellerId));
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{id}/bids")
    @Operation(
        summary = "Place a bid on an auction",
        description = "Submits a bid. Amount must exceed currentPrice + minimumIncrement. " +
                      "If placed within the last 2 minutes, the auction end time is extended (Anti-Sniping rule). " +
                      "Concurrent bids are protected by a Redisson distributed lock."
    )
    @ApiResponse(responseCode = "201", description = "Bid placed successfully")
    @ApiResponse(responseCode = "400", description = "Amount too low, auction not active, seller bidding own auction, or insufficient wallet balance")
    @ApiResponse(responseCode = "401", description = "Missing X-User-Id header")
    @ApiResponse(responseCode = "404", description = "Auction not found")
    public ResponseEntity<BidResponse> placeBid(
            @Parameter(description = "Auction ID", required = true) @PathVariable String id,
            @Valid @RequestBody PlaceBidRequest req,
            @Parameter(hidden = true) @RequestAttribute("userId") String bidderId) {
        Bid bid = auctionService.placeBid(id, bidderId, req.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(BidResponse.from(bid));
    }

    @GetMapping("/{id}/bids")
    @Operation(summary = "Get bid history", description = "Returns all bids for the given auction, sorted by amount descending.")
    @ApiResponse(responseCode = "200", description = "Bid history retrieved")
    @ApiResponse(responseCode = "404", description = "Auction not found")
    public ResponseEntity<List<BidResponse>> getBidHistory(
            @Parameter(description = "Auction ID", required = true) @PathVariable String id) {
        List<BidResponse> bids = auctionService.getBidHistory(id);
        return ResponseEntity.ok(bids);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Subscribe to live auction updates (SSE)",
        description = "Opens a Server-Sent Events stream. The server pushes a JSON bid event every time a new bid is placed on this auction."
    )
    @ApiResponse(
        responseCode = "200", 
        description = "SSE stream opened successfully",
        content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)
    )
    public SseEmitter streamAuction(
            @Parameter(description = "Auction ID", required = true) @PathVariable String id) {
        return sseEmitterService.subscribe(id);
    }
}
