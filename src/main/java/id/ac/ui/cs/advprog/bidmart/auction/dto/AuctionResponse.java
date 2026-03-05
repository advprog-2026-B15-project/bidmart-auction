package id.ac.ui.cs.advprog.bidmart.auction.dto;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class AuctionResponse {

    private String id;
    private String listingId;
    private String sellerId;
    private String title;
    private Long startingPrice;
    private Long reservePrice;
    private Long minimumIncrement;
    private Long currentBid;
    private AuctionStatus status;
    private OffsetDateTime endTime;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static AuctionResponse from(Auction auction) {
        AuctionResponse res = new AuctionResponse();
        res.id = auction.getId();
        res.listingId = auction.getListingId();
        res.sellerId = auction.getSellerId();
        res.title = auction.getTitle();
        res.startingPrice = auction.getStartingPrice();
        res.reservePrice = auction.getReservePrice();
        res.minimumIncrement = auction.getMinimumIncrement();
        res.currentBid = auction.getCurrentBid();
        res.status = auction.getStatus();
        res.endTime = auction.getEndTime();
        res.createdAt = auction.getCreatedAt();
        res.updatedAt = auction.getUpdatedAt();
        return res;
    }
}