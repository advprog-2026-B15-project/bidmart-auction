package id.ac.ui.cs.advprog.bidmart.auction.controller;

import id.ac.ui.cs.advprog.bidmart.auction.service.AuctionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/auction")
public class AuctionController {
    @Autowired
    private AuctionService auctionService;

    @GetMapping("/list")
    public String listAuctions(Model model) {
        model.addAttribute("auctions", auctionService.findAll());
        return "auction-list";
    }

    @PostMapping("/add")
    public String addAuction(@RequestParam String title, @RequestParam Double initialBid) {
        auctionService.create(title, initialBid);
        return "redirect:/auction/list";
    }

    @PostMapping("/activate/{id}")
    public String activateAuction(@PathVariable Long id) {
        auctionService.activate(id);
        return "redirect:/auction/list";
    }
}