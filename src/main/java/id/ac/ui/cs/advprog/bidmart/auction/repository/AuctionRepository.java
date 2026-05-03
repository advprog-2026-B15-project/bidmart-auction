package id.ac.ui.cs.advprog.bidmart.auction.repository;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AuctionRepository extends JpaRepository<Auction, String> {
    List<Auction> findBySellerId(String sellerId);
    List<Auction> findByStatus(AuctionStatus status);
    List<Auction> findByListingId(String listingId);

    @Query("SELECT a FROM Auction a WHERE a.status IN :statuses AND a.endTime < :now")
    List<Auction> findExpiredByStatuses(@Param("statuses") List<AuctionStatus> statuses, @Param("now") OffsetDateTime now);
}