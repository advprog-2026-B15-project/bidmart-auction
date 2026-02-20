package id.ac.ui.cs.advprog.bidmart.auction.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Auction {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String title;
    private Double currentBid;

    @Enumerated(EnumType.STRING)
    private AuctionStatus status = AuctionStatus.DRAFT;
}