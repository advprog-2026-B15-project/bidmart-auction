package id.ac.ui.cs.advprog.bidmart.auction.service.adapter;

import id.ac.ui.cs.advprog.bidmart.auction.config.RabbitMQConfig;
import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionClosedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.dto.BidPlacedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RabbitMQAdapter implements AuctionEventPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishBidPlaced(BidPlacedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY_BID_PLACED,
                event
        );
    }

    @Override
    public void publishWinnerDetermined(WinnerDeterminedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY_WINNER_DETERMINED,
                event
        );
    }

    @Override
    public void publishAuctionClosed(AuctionClosedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY_AUCTION_CLOSED,
                event
        );
    }
}
