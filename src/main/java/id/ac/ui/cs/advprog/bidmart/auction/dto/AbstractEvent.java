package id.ac.ui.cs.advprog.bidmart.auction.dto;

import java.time.OffsetDateTime;
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
public abstract class AbstractEvent {
    private String eventId;
    private String eventType;
    private Integer eventVersion;
    private OffsetDateTime occurredAt;
    private String source;
}
