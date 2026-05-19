package id.ac.ui.cs.advprog.bidmart.auction.dto;

import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class BidResponse implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String auctionId;
    private String bidderId;
    private Long amount;
    private OffsetDateTime createdAt;

    public static BidResponse from(Bid bid) {
        BidResponse res = new BidResponse();
        res.id = bid.getId();
        res.auctionId = bid.getAuction().getId();
        res.bidderId = bid.getBidderId();
        res.amount = bid.getAmount();
        res.createdAt = bid.getCreatedAt();
        return res;
    }
}