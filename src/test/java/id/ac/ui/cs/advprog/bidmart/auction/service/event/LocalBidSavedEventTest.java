package id.ac.ui.cs.advprog.bidmart.auction.service.event;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocalBidSavedEventTest {

    @Test
    void testLocalBidSavedEventCreation() {
        Auction auction = new Auction();
        auction.setId("auc-1");
        
        Bid bid = new Bid();
        bid.setId("bid-1");
        
        Object source = new Object();
        
        LocalBidSavedEvent event = new LocalBidSavedEvent(source, auction, bid);
        
        assertNotNull(event);
        assertEquals(source, event.getSource());
        assertEquals(auction, event.getAuction());
        assertEquals(bid, event.getBid());
    }
}
