package id.ac.ui.cs.advprog.bidmart.auction.dto;

import java.util.List;
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
public class WinnerDeterminedEvent extends AbstractEvent {

    private Payload payload;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    public static class Payload extends BasePayload {
        private String winnerUserId;
        private Long finalPrice;
        private String currency;
        private String itemName;
        private Integer quantity;
        private List<String> loserUserIds;
    }
}
