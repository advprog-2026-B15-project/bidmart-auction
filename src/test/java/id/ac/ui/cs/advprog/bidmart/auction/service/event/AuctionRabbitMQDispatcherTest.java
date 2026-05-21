package id.ac.ui.cs.advprog.bidmart.auction.service.event;

import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionClosedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionRabbitMQDispatcherTest {

    @Mock
    private AuctionEventPort auctionEventPort;

    @InjectMocks
    private AuctionRabbitMQDispatcher dispatcher;

    @Test
    void testOnPublishWinner() {
        WinnerDeterminedEvent.Payload payload = WinnerDeterminedEvent.Payload.builder()
                .auctionId("auc-1")
                .build();
        WinnerDeterminedEvent eventPayload = WinnerDeterminedEvent.builder()
                .payload(payload)
                .build();
        PublishWinnerRabbitEvent event = new PublishWinnerRabbitEvent(this, eventPayload);

        dispatcher.onPublishWinner(event);

        verify(auctionEventPort).publishWinnerDetermined(eventPayload);
    }

    @Test
    void testOnPublishUnsold() {
        AuctionClosedEvent.Payload payload = AuctionClosedEvent.Payload.builder()
                .auctionId("auc-1")
                .build();
        AuctionClosedEvent eventPayload = AuctionClosedEvent.builder()
                .payload(payload)
                .build();
        PublishUnsoldRabbitEvent event = new PublishUnsoldRabbitEvent(this, eventPayload);

        dispatcher.onPublishUnsold(event);

        verify(auctionEventPort).publishAuctionClosed(eventPayload);
    }
}
