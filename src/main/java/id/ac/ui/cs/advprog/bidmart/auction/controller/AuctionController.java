package id.ac.ui.cs.advprog.bidmart.auction.controller;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Controller
@RequestMapping("/auction")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    @GetMapping("/list")
    public String listAuctions(Model model) {
        model.addAttribute("auctions", auctionService.findAll());
        return "auction-list";
    }

    @PostMapping("/activate/{id}")
    public String activateAuction(@PathVariable String id) {
        auctionService.activate(id);
        return "redirect:/auction/list";
    }

    @PostMapping("/add")
    public String addAuction(@RequestParam String title) {
        Auction auction = new Auction();
        auction.setTitle(title);
        auction.setListingId("dummy-listing");
        auction.setSellerId("dummy-seller");
        auction.setStartingPrice(100000L);
        auction.setMinimumIncrement(10000L);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
        auctionService.create(auction);
        return "redirect:/auction/list";
    }
}