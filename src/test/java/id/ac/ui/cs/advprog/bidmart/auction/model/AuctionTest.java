package id.ac.ui.cs.advprog.bidmart.auction.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuctionTest {

    private Auction auction;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        auction = new Auction();
        now = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @Test
    void testPrePersist() {
        auction.prePersist();

        assertEquals(AuctionStatus.DRAFT, auction.getStatus());
        assertEquals(0L, auction.getCurrentPrice());
        assertNotNull(auction.getCreatedAt());
        assertNotNull(auction.getUpdatedAt());
    }

    @Test
    void testPreUpdate() {
        auction.preUpdate();
        assertNotNull(auction.getUpdatedAt());
    }

    @Test
    void testApplyAntiSnipingRule_StatusActive() {
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(now.plusMinutes(1)); // Less than 2 mins left

        OffsetDateTime originalEndTime = auction.getEndTime();
        auction.applyAntiSnipingRule();

        assertTrue(auction.getEndTime().isAfter(originalEndTime));
        assertEquals(AuctionStatus.EXTENDED, auction.getStatus());
    }

    @Test
    void testApplyAntiSnipingRule_StatusExtended() {
        auction.setStatus(AuctionStatus.EXTENDED);
        auction.setEndTime(now.plusMinutes(1));

        OffsetDateTime originalEndTime = auction.getEndTime();
        auction.applyAntiSnipingRule();

        assertTrue(auction.getEndTime().isAfter(originalEndTime));
        assertEquals(AuctionStatus.EXTENDED, auction.getStatus()); // Stays EXTENDED
    }

    @Test
    void testApplyAntiSnipingRule_NoAntiSniping_MoreThan2MinsLeft() {
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(now.plusMinutes(5));

        OffsetDateTime originalEndTime = auction.getEndTime();
        auction.applyAntiSnipingRule();

        assertEquals(originalEndTime, auction.getEndTime()); // Not changed
        assertEquals(AuctionStatus.ACTIVE, auction.getStatus());
    }

}
