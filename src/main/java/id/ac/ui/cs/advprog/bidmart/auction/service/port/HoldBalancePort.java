package id.ac.ui.cs.advprog.bidmart.auction.service.port;

public interface HoldBalancePort {
    void holdBalance(String userId, String auctionId, Long amount);
}
