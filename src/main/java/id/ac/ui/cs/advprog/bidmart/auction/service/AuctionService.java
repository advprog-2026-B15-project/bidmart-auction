package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.dto.CreateAuctionRequest;
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

    public Auction create(CreateAuctionRequest req, String sellerId) {
        if (req.getReservePrice() != null
                && req.getReservePrice() <= req.getStartingPrice()) {
            throw new IllegalArgumentException("Reserve price harus lebih besar dari starting price");
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

    public Auction activate(String auctionId, String sellerId) {
        Auction auction = findById(auctionId);

        if (!auction.getSellerId().equals(sellerId)) {
            throw new IllegalStateException("Hanya seller pemilik yang bisa mengaktifkan lelang");
        }

        if (auction.getStatus() != AuctionStatus.DRAFT) {
            throw new IllegalStateException("Hanya auction berstatus DRAFT yang bisa diaktifkan");
        }

        auction.setStatus(AuctionStatus.ACTIVE); // DRAFT -> ACTIVE
        return auctionRepository.save(auction);
    }
}