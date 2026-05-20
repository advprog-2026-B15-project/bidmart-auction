package id.ac.ui.cs.advprog.bidmart.auction.service.event;

import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PublishWinnerRabbitEventTest {

    @Test
    void testPublishWinnerRabbitEventCreation() {
        WinnerDeterminedEvent.Payload payload = WinnerDeterminedEvent.Payload.builder()
                .auctionId("auc-1")
                .build();
        WinnerDeterminedEvent rabbitEvent = WinnerDeterminedEvent.builder()
                .payload(payload)
                .build();
        
        Object source = new Object();
        
        PublishWinnerRabbitEvent event = new PublishWinnerRabbitEvent(source, rabbitEvent);
        
        assertNotNull(event);
        assertEquals(source, event.getSource());
        assertEquals(rabbitEvent, event.getRabbitEventPayload());
    }
}
