package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AuctionService {
    @Autowired
    private AuctionRepository auctionRepository;

    public List<Auction> findAll() {
        return auctionRepository.findAll();
    }

    public Auction create(String title, Double initialBid) {
        Auction auction = new Auction();
        auction.setTitle(title);
        auction.setCurrentBid(initialBid);
        auction.setStatus(AuctionStatus.DRAFT);
        return auctionRepository.save(auction);
    }

    public Auction activate(Long id) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid auction Id:" + id));
        auction.setStatus(AuctionStatus.ACTIVE);
        return auctionRepository.save(auction);
    }
}