package id.ac.ui.cs.advprog.bidmart.auction.service.adapter;

import id.ac.ui.cs.advprog.bidmart.auction.config.RabbitMQConfig;
import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionClosedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.dto.BidPlacedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMQAdapterTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RabbitMQAdapter rabbitMQAdapter;

    @Test
    void testPublishBidPlaced() {
        BidPlacedEvent event = BidPlacedEvent.builder().build();
        rabbitMQAdapter.publishBidPlaced(event);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.ROUTING_KEY_BID_PLACED),
                eq(event)
        );
    }

    @Test
    void testPublishWinnerDetermined() {
        WinnerDeterminedEvent event = WinnerDeterminedEvent.builder().build();
        rabbitMQAdapter.publishWinnerDetermined(event);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.ROUTING_KEY_WINNER_DETERMINED),
                eq(event)
        );
    }

    @Test
    void testPublishAuctionClosed() {
        AuctionClosedEvent event = AuctionClosedEvent.builder().build();
        rabbitMQAdapter.publishAuctionClosed(event);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.ROUTING_KEY_AUCTION_CLOSED),
                eq(event)
        );
    }
}
