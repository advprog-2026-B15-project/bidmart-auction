package id.ac.ui.cs.advprog.bidmart.auction.service.event;

import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PublishWinnerRabbitEvent extends ApplicationEvent {
    private final WinnerDeterminedEvent rabbitEventPayload;

    public PublishWinnerRabbitEvent(Object source, WinnerDeterminedEvent rabbitEventPayload) {
        super(source);
        this.rabbitEventPayload = rabbitEventPayload;
    }
}
