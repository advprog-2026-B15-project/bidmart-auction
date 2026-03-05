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

    @NotBlank(message = "Listing ID tidak boleh kosong")
    private String listingId;

    @NotBlank(message = "Title tidak boleh kosong")
    private String title;

    @NotNull(message = "Starting price tidak boleh kosong")
    @Min(value = 1, message = "Starting price harus lebih dari 0")
    private Long startingPrice;

    @Min(value = 1, message = "Reserve price harus lebih dari 0")
    private Long reservePrice;

    @NotNull(message = "Minimum increment tidak boleh kosong")
    @Min(value = 1, message = "Minimum increment harus lebih dari 0")
    private Long minimumIncrement;

    @NotNull(message = "End time tidak boleh kosong")
    @Future(message = "End time harus di masa depan")
    private OffsetDateTime endTime;
}
