package id.ac.ui.cs.advprog.bidmart.auction.service.event;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class LocalBidSavedEvent extends ApplicationEvent {
    private final Auction auction;
    private final Bid bid;

    public LocalBidSavedEvent(Object source, Auction auction, Bid bid) {
        super(source);
        this.auction = auction;
        this.bid = bid;
    }
}
