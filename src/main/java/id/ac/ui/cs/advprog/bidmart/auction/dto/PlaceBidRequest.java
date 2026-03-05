package id.ac.ui.cs.advprog.bidmart.auction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlaceBidRequest {

    @NotNull(message = "Amount tidak boleh kosong")
    @Min(value = 1, message = "Amount harus lebih dari 0")
    private Long amount;
}