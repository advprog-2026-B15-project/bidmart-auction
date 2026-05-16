package id.ac.ui.cs.advprog.bidmart.auction.dto;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;

class UpdateAuctionRequestTest {

    @Test
    void testGettersAndSetters() {
        UpdateAuctionRequest req = new UpdateAuctionRequest();
        OffsetDateTime now = OffsetDateTime.now();

        req.setTitle("Title");
        req.setStartingPrice(100L);
        req.setReservePrice(200L);
        req.setMinimumIncrement(10L);
        req.setEndTime(now);

        assertEquals("Title", req.getTitle());
        assertEquals(100L, req.getStartingPrice());
        assertEquals(200L, req.getReservePrice());
        assertEquals(10L, req.getMinimumIncrement());
        assertEquals(now, req.getEndTime());
    }
}
