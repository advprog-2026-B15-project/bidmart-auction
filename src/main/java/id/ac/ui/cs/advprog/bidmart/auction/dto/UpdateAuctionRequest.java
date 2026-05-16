package id.ac.ui.cs.advprog.bidmart.auction.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class UpdateAuctionRequest {

    private String title;

    @Min(value = 1, message = "Starting price must be strictly greater than 0")
    private Long startingPrice;

    @Min(value = 1, message = "Reserve price must be strictly greater than 0")
    private Long reservePrice;

    @Min(value = 1, message = "Minimum increment must be strictly greater than 0")
    private Long minimumIncrement;

    @Future(message = "End time must be in the future")
    private OffsetDateTime endTime;
}
