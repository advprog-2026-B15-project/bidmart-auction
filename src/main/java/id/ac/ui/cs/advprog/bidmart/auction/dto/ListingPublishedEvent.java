package id.ac.ui.cs.advprog.bidmart.auction.dto;

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
public class ListingPublishedEvent extends AbstractEvent {
    private Payload payload;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        private String listingId;
        private String title;
        private String sellerId;
        private Double startingPrice;
        private Double reservePrice;
        private String endTime;
    }
}
