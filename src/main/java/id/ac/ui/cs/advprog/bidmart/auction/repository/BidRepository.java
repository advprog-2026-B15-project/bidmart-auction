package id.ac.ui.cs.advprog.bidmart.auction.repository;

import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, String> {
    // semua bid di lelang ini, diurutkan dari tertinggi
    List<Bid> findByAuctionIdOrderByAmountDesc(String auctionId);

    // bid tertinggi di lelang ini
    Optional<Bid> findTopByAuctionIdOrderByAmountDesc(String auctionId);
}
