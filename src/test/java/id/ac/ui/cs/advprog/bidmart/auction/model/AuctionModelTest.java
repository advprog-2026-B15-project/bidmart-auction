package id.ac.ui.cs.advprog.bidmart.auction.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AuctionModelTest {

    @Test
    void testPrePersistSetsDefaultsWhenNull() {
        // Semua field null sebelum persist
        Auction auction = new Auction();
        assertNull(auction.getStatus());
        assertNull(auction.getCurrentPrice());
        assertNull(auction.getCreatedAt());
        assertNull(auction.getUpdatedAt());

        auction.prePersist();

        // Semua field terisi default
        assertEquals(AuctionStatus.DRAFT, auction.getStatus());
        assertEquals(0L, auction.getCurrentPrice());
        assertNotNull(auction.getCreatedAt());
        assertNotNull(auction.getUpdatedAt());
    }

    @Test
    void testPrePersistDoesNotOverrideExistingValues() {
        // Semua field sudah terisi sebelum persist
        Auction auction = new Auction();
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setCurrentPrice(500000L);
        OffsetDateTime existingTime = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        auction.setCreatedAt(existingTime);
        auction.setUpdatedAt(existingTime);

        auction.prePersist();

        // Field tidak ditimpa
        assertEquals(AuctionStatus.ACTIVE, auction.getStatus());
        assertEquals(500000L, auction.getCurrentPrice());
        assertEquals(existingTime, auction.getCreatedAt());
        assertEquals(existingTime, auction.getUpdatedAt());
    }

    @Test
    void testPreUpdateSetsUpdatedAt() {
        Auction auction = new Auction();
        auction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        OffsetDateTime before = auction.getUpdatedAt();

        auction.preUpdate();

        // updatedAt harus berubah ke waktu terkini
        assertTrue(auction.getUpdatedAt().isAfter(before));
    }

    @Test
    void testBidPrePersistSetsCreatedAtWhenNull() {
        Bid bid = new Bid();
        assertNull(bid.getCreatedAt());

        bid.prePersist();

        assertNotNull(bid.getCreatedAt());
    }

    @Test
    void testBidPrePersistDoesNotOverrideExistingCreatedAt() {
        Bid bid = new Bid();
        OffsetDateTime existing = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        bid.setCreatedAt(existing);

        bid.prePersist();

        assertEquals(existing, bid.getCreatedAt());
    }
}
