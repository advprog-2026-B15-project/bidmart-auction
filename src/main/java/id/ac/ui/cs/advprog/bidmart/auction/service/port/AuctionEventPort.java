package id.ac.ui.cs.advprog.bidmart.auction.service.port;

import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionClosedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.dto.BidPlacedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;

public interface AuctionEventPort {
    void publishBidPlaced(BidPlacedEvent event);
    void publishWinnerDetermined(WinnerDeterminedEvent event);
    void publishAuctionClosed(AuctionClosedEvent event);
}
