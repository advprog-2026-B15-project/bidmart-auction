package id.ac.ui.cs.advprog.bidmart.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BidPlacedEvent {
    private String eventId;
    private String eventType;
    private Integer eventVersion;
    private OffsetDateTime occurredAt;
    private String source;
    private Payload payload;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        private String bidId;
        private String auctionId;
        private String listingId;
        private String bidderId;
        private String previousBidderId;
        private Long amount;
    }
}
