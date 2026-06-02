package id.ac.ui.cs.advprog.bidmart.auction.service.event;

import id.ac.ui.cs.advprog.bidmart.auction.dto.ListingPublishedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.service.AuctionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingPublishedRabbitListenerTest {

    @Mock
    private AuctionService auctionService;

    @InjectMocks
    private ListingPublishedRabbitListener listener;

    @Test
    void testOnListingPublishedSuccess() {
        ListingPublishedEvent event = ListingPublishedEvent.builder()
                .eventId("event-123")
                .build();

        listener.onListingPublished(event);

        verify(auctionService, times(1)).createFromListingPublishedEvent(event);
    }

    @Test
    void testOnListingPublishedException() {
        ListingPublishedEvent event = ListingPublishedEvent.builder()
                .eventId("event-123")
                .build();

        doThrow(new RuntimeException("Database error")).when(auctionService).createFromListingPublishedEvent(event);

        assertThrows(RuntimeException.class, () -> listener.onListingPublished(event));

        verify(auctionService, times(1)).createFromListingPublishedEvent(event);
    }
}
