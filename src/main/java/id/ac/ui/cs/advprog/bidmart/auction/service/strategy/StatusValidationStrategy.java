package id.ac.ui.cs.advprog.bidmart.auction.service.strategy;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import org.springframework.stereotype.Component;
import java.util.EnumSet;

@Component
public class StatusValidationStrategy implements BidValidationStrategy {
    private static final EnumSet<AuctionStatus> ALLOWED_STATUSES = EnumSet.of(AuctionStatus.ACTIVE, AuctionStatus.EXTENDED);
    @Override
    public void validate(Auction auction, Long bidAmount) {
        if (!ALLOWED_STATUSES.contains(auction.getStatus())) {
            throw new IllegalStateException("Cannot place bid on auction with status: " + auction.getStatus());
        }

        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        if (auction.getEndTime() != null && now.isAfter(auction.getEndTime())) {
            throw new IllegalStateException("Cannot place bid on expired auction. Time ended at: " + auction.getEndTime());
        }
    }
}
