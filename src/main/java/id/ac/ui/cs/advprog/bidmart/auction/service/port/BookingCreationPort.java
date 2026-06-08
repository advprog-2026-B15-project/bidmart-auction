package id.ac.ui.cs.advprog.bidmart.auction.service.port;

import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;

public interface BookingCreationPort {
    void createBookingFromWinner(WinnerDeterminedEvent event);
    void markBookingPaid(String auctionId);
}
