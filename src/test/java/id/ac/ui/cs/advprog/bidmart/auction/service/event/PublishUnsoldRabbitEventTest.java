package id.ac.ui.cs.advprog.bidmart.auction.service.event;

import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionClosedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PublishUnsoldRabbitEventTest {

    @Test
    void testPublishUnsoldRabbitEventCreation() {
        AuctionClosedEvent.Payload payload = AuctionClosedEvent.Payload.builder()
                .auctionId("auc-1")
                .build();
        AuctionClosedEvent rabbitEvent = AuctionClosedEvent.builder()
                .payload(payload)
                .build();
        
        Object source = new Object();
        
        PublishUnsoldRabbitEvent event = new PublishUnsoldRabbitEvent(source, rabbitEvent);
        
        assertNotNull(event);
        assertEquals(source, event.getSource());
        assertEquals(rabbitEvent, event.getRabbitEventPayload());
    }
}
