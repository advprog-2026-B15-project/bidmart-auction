package id.ac.ui.cs.advprog.bidmart.auction.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class CreateAuctionRequest {

    @NotBlank(message = "Listing ID cannot be empty")
    private String listingId;

    @NotBlank(message = "Title cannot be empty")
    private String title;

    @NotNull(message = "Starting price cannot be null")
    @Min(value = 1, message = "Starting price must be strictly greater than 0")
    private Long startingPrice;

    @Min(value = 1, message = "Reserve price must be strictly greater than 0")
    private Long reservePrice;

    @NotNull(message = "Minimum increment cannot be null")
    @Min(value = 1, message = "Minimum increment must be strictly greater than 0")
    private Long minimumIncrement;

    @NotNull(message = "End time cannot be null")
    @Future(message = "End time must be in the future")
    private OffsetDateTime endTime;
}
