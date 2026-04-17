package id.ac.ui.cs.advprog.bidmart.auction.service.adapter;

import id.ac.ui.cs.advprog.bidmart.auction.config.RabbitMQConfig;
import id.ac.ui.cs.advprog.bidmart.auction.dto.BidPlacedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMQAdapterTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RabbitMQAdapter rabbitMQAdapter;

    @Test
    void testPublishBidPlaced() {
        BidPlacedEvent event = BidPlacedEvent.builder()
                .eventId("evt-001")
                .eventType("BidPlaced")
                .eventVersion(1)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .source("auction-service")
                .payload(BidPlacedEvent.Payload.builder()
                        .bidId("bid-001")
                        .auctionId("auction-001")
                        .listingId("listing-001")
                        .sellerUserId("seller-001")
                        .bidderUserId("user-001")
                        .previousBidderUserId(null)
                        .bidAmount(500000L)
                        .itemName("Test Item")
                        .build())
                .build();

        rabbitMQAdapter.publishBidPlaced(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY_BID_PLACED,
                event
        );
    }
}
