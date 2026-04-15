package id.ac.ui.cs.advprog.bidmart.auction.service.strategy;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class StatusValidationStrategyTest {

    private final StatusValidationStrategy strategy = new StatusValidationStrategy();

    @Test
    void testValidateActiveStatus() {
        Auction auction = new Auction();
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        assertDoesNotThrow(() -> strategy.validate(auction, 100L));
    }

    @Test
    void testValidateExtendedStatus() {
        Auction auction = new Auction();
        auction.setStatus(AuctionStatus.EXTENDED);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        assertDoesNotThrow(() -> strategy.validate(auction, 100L));
    }

    @Test
    void testValidateDraftStatusThrows() {
        Auction auction = new Auction();
        auction.setStatus(AuctionStatus.DRAFT);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        assertThrows(IllegalStateException.class, () -> strategy.validate(auction, 100L));
    }

    @Test
    void testValidatePassedEndTimeThrows() {
        Auction auction = new Auction();
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

        assertThrows(IllegalStateException.class, () -> strategy.validate(auction, 100L));
    }

    @Test
    void testValidateNullEndTimeDoesNotThrow() {
        Auction auction = new Auction();
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(null);

        assertDoesNotThrow(() -> strategy.validate(auction, 100L));
    }
}
