package id.ac.ui.cs.advprog.bidmart.auction.service.strategy;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;

public interface BidValidationStrategy {
    void validate(Auction auction, Long bidAmount);
}
