package id.ac.ui.cs.advprog.bidmart.auction.repository;

import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, String> {
    @Query("SELECT b FROM Bid b WHERE b.auction.id = :auctionId ORDER BY b.amount DESC")
    List<Bid> findBidHistory(@Param("auctionId") String auctionId);

    @Query("SELECT b FROM Bid b WHERE b.auction.id = :auctionId ORDER BY b.amount DESC LIMIT 1")
    Optional<Bid> findHighestBid(@Param("auctionId") String auctionId);

    @Query("SELECT DISTINCT b.bidderId FROM Bid b WHERE b.auction.id = :auctionId")
    List<String> findDistinctBidderIdsByAuctionId(@Param("auctionId") String auctionId);

    @Query("SELECT DISTINCT b.bidderId FROM Bid b WHERE b.auction.id = :auctionId AND b.bidderId != :winnerId")
    List<String> findDistinctLoserBidderIds(@Param("auctionId") String auctionId, @Param("winnerId") String winnerId);
}
