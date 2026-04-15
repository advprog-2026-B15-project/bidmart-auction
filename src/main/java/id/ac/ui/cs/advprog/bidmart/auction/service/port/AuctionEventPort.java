package id.ac.ui.cs.advprog.bidmart.auction.service.port;

import id.ac.ui.cs.advprog.bidmart.auction.dto.BidPlacedEvent;

public interface AuctionEventPort {
    void publishBidPlaced(BidPlacedEvent event);
}
