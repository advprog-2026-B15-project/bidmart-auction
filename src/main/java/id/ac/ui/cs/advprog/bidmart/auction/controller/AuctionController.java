package id.ac.ui.cs.advprog.bidmart.auction.controller;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/auction")
public class AuctionController {
    @Autowired
    private AuctionRepository auctionRepository;

    @GetMapping("/list")
    public String listAuctions(Model model) {
        model.addAttribute("auctions", auctionRepository.findAll());
        return "auction-list";
    }

    @PostMapping("/add")
    public String addAuction(@RequestParam String title, @RequestParam Double initialBid) {
        Auction auction = new Auction();
        auction.setTitle(title);
        auction.setCurrentBid(initialBid);
        auctionRepository.save(auction);
        return "redirect:/auction/list";
    }
}
