package id.ac.ui.cs.advprog.bidmart.auction.service.event;

import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRabbitMQDispatcher {

    private final AuctionEventPort auctionEventPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublishWinner(PublishWinnerRabbitEvent event) {
        log.info("Transaction committed. Dispatching WinnerDeterminedEvent for auction {}", 
                 event.getRabbitEventPayload().getPayload().getAuctionId());
        auctionEventPort.publishWinnerDetermined(event.getRabbitEventPayload());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublishUnsold(PublishUnsoldRabbitEvent event) {
        log.info("Transaction committed. Dispatching AuctionClosedEvent (UNSOLD) for auction {}", 
                 event.getRabbitEventPayload().getPayload().getAuctionId());
        auctionEventPort.publishAuctionClosed(event.getRabbitEventPayload());
    }
}
