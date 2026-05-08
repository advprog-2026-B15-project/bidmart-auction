package id.ac.ui.cs.advprog.bidmart.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BasePayload {
    private String auctionId;
    private String listingId;
    private String sellerUserId;
}
