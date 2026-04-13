package id.ac.ui.cs.advprog.bidmart.auction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlaceBidRequest {

    @NotNull(message = "Amount cannot be null")
    @Min(value = 1, message = "Amount must be strictly greater than 0")
    private Long amount;
}