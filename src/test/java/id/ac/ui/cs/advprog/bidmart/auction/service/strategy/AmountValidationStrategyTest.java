package id.ac.ui.cs.advprog.bidmart.auction.service.strategy;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AmountValidationStrategyTest {

    private final AmountValidationStrategy strategy = new AmountValidationStrategy();

    @Test
    void testValidateValidAmountWithNoCurrentPrice() {
        Auction auction = new Auction();
        auction.setStartingPrice(100L);
        auction.setMinimumIncrement(10L);
        auction.setCurrentPrice(0L);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        assertDoesNotThrow(() -> strategy.validate(auction, 100L));
    }

    @Test
    void testValidateInvalidAmountWithNoCurrentPrice() {
        Auction auction = new Auction();
        auction.setStartingPrice(100L);
        auction.setMinimumIncrement(10L);
        auction.setCurrentPrice(0L);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        assertThrows(IllegalArgumentException.class, () -> strategy.validate(auction, 99L));
    }

    @Test
    void testValidateValidAmountWithCurrentPrice() {
        Auction auction = new Auction();
        auction.setStartingPrice(100L);
        auction.setMinimumIncrement(10L);
        auction.setCurrentPrice(150L);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        assertDoesNotThrow(() -> strategy.validate(auction, 160L));
    }

    @Test
    void testValidateInvalidAmountWithCurrentPrice() {
        Auction auction = new Auction();
        auction.setStartingPrice(100L);
        auction.setMinimumIncrement(10L);
        auction.setCurrentPrice(150L);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        assertThrows(IllegalArgumentException.class, () -> strategy.validate(auction, 159L));
    }

    @Test
    void testValidateWithNullMinimumIncrementFallsBackToOne() {
        Auction auction = new Auction();
        auction.setStartingPrice(100L);
        auction.setMinimumIncrement(null);
        auction.setCurrentPrice(150L);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        assertDoesNotThrow(() -> strategy.validate(auction, 151L));

        assertThrows(IllegalArgumentException.class, () -> strategy.validate(auction, 150L));
    }

    @Test
    void testValidateWithNullCurrentPriceFallsBackToStartingPrice() {
        Auction auction = new Auction();
        auction.setStartingPrice(200L);
        auction.setMinimumIncrement(10L);
        auction.setCurrentPrice(null); 
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

        assertDoesNotThrow(() -> strategy.validate(auction, 200L));

        assertThrows(IllegalArgumentException.class, () -> strategy.validate(auction, 199L));
    }
}
