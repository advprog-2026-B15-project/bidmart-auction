package id.ac.ui.cs.advprog.bidmart.auction.dto;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AuctionClosedEvent extends AbstractEvent {

    private Payload payload;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        private String auctionId;
        private String listingId;
        private String sellerUserId;
        private OffsetDateTime closedAt;
        private List<String> allBidderIds;
    }
}
