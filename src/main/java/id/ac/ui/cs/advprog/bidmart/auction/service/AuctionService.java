package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private final AuctionRepository auctionRepository;

    public List<Auction> findAll() {
        return auctionRepository.findAll();
    }

    public Auction findById(String id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Auction tidak ditemukan"));
    }

    public Auction create(Auction auction) {
        return auctionRepository.save(auction);
    }

    public Auction activate(String id) {
        Auction auction = findById(id);

        if (auction.getStatus() != AuctionStatus.DRAFT) {
            throw new IllegalStateException("Hanya auction berstatus DRAFT yang bisa diaktifkan");
        }

        auction.setStatus(AuctionStatus.ACTIVE);
        return auctionRepository.save(auction);
    }
}