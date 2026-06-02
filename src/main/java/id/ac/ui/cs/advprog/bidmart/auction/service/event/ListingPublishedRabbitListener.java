package id.ac.ui.cs.advprog.bidmart.auction.service.event;

import id.ac.ui.cs.advprog.bidmart.auction.config.RabbitMQConfig;
import id.ac.ui.cs.advprog.bidmart.auction.dto.ListingPublishedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.service.AuctionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListingPublishedRabbitListener {

    private final AuctionService auctionService;

    @RabbitListener(queues = RabbitMQConfig.LISTING_PUBLISHED_QUEUE)
    public void onListingPublished(ListingPublishedEvent event) {
        log.info("Received ListingPublishedEvent id={}", event.getEventId());
        try {
            auctionService.createFromListingPublishedEvent(event);
        } catch (Exception e) {
            log.error("Failed to process ListingPublishedEvent id={}", event.getEventId(), e);
            throw e;
        }
    }
}
