package id.ac.ui.cs.advprog.bidmart.auction.service.event;

import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionClosedEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PublishUnsoldRabbitEvent extends ApplicationEvent {
    private final AuctionClosedEvent rabbitEventPayload;

    public PublishUnsoldRabbitEvent(Object source, AuctionClosedEvent rabbitEventPayload) {
        super(source);
        this.rabbitEventPayload = rabbitEventPayload;
    }
}
