package id.ac.ui.cs.advprog.bidmart.auction.controller;

import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionResponse;
import id.ac.ui.cs.advprog.bidmart.auction.dto.CreateAuctionRequest;
import id.ac.ui.cs.advprog.bidmart.auction.service.AuctionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
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
            @RequestHeader("X-Seller-Id") String sellerId) {
        AuctionResponse res = AuctionResponse.from(auctionService.create(req, sellerId));
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<AuctionResponse> activate(
            @PathVariable String id,
            @RequestHeader("X-Seller-Id") String sellerId) {
        AuctionResponse res = AuctionResponse.from(auctionService.activate(id, sellerId));
        return ResponseEntity.ok(res);
    }
}