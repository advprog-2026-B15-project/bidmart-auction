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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ExceptionHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
@Tag(name = "Auction", description = "API for Auction Management")
public class AuctionController {

    private final AuctionService auctionService;

    @GetMapping
    public ResponseEntity<List<AuctionResponse>> findAll() {
        List<AuctionResponse> auctions = auctionService.findAll()
                .stream()
                .map(AuctionResponse::from)
                .toList();
        return ResponseEntity.ok(auctions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuctionResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(AuctionResponse.from(auctionService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<AuctionResponse> create(
            @Valid @RequestBody CreateAuctionRequest req,
            @RequestHeader("X-User-Id") String sellerId) {
        AuctionResponse res = AuctionResponse.from(auctionService.create(req, sellerId));
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<AuctionResponse> activate(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String sellerId) {
        AuctionResponse res = AuctionResponse.from(auctionService.activate(id, sellerId));
        return ResponseEntity.ok(res);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleBadState(IllegalStateException e) {
        if (e.getMessage().toLowerCase().contains("owner")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
        // TODO: Konfirmasi dengan PJ Wallet terkait bentuk final response error code-nya
        if (e.getMessage().contains("403")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
        if (e.getMessage().contains("500")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @PostMapping("/{id}/bids")
    @Operation(summary = "Place a new bid on an auction", 
        description = "Submit a bid for a specific auction. Validates the amount " +
        "and extends the auction time if placed within the last 2 minutes (Anti-Sniping).")
    public ResponseEntity<BidResponse> placeBid(
            @PathVariable String id,
            @Valid @RequestBody PlaceBidRequest req,
            @RequestHeader("X-User-Id") String bidderId) {
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
}
