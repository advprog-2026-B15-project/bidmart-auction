package id.ac.ui.cs.advprog.bidmart.auction;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void run(String... args) {
        log.info("[DataSeeder] Cleaning up existing data for a fresh start...");
        bidRepository.deleteAll();
        auctionRepository.deleteAll();

        log.info("[DataSeeder] Seeding initial data with various scenarios...");

        seedScenarios();
        seedBulkData(45);

        log.info("[DataSeeder] Done seeding database.");
    }

    private void seedScenarios() {
        // DRAFT (New item)
        createAuction("Vintage Camera", "listing-001", "seller-1@mail.com", 500000L, AuctionStatus.DRAFT, 7);

        // ACTIVE (No bids yet)
        createAuction("Gaming Laptop", "listing-002", "seller-2@mail.com", 15000000L, AuctionStatus.ACTIVE, 3);

        // ACTIVE (With high activity)
        Auction hotAuction = createAuction("Limited Edition Sneakers", "listing-003", "seller-3@mail.com", 2000000L, AuctionStatus.ACTIVE, 5);
        hotAuction = addBid(hotAuction, "buyer-1@mail.com", 2100000L);
        hotAuction = addBid(hotAuction, "buyer-2@mail.com", 2300000L);
        hotAuction = addBid(hotAuction, "buyer-3@mail.com", 2500000L);

        // EXTENDED (Anti-sniping triggered)
        Auction extended = new Auction();
        extended.setTitle("Antique Gold Coin");
        extended.setListingId("listing-004");
        extended.setSellerId("seller-1@mail.com");
        extended.setStartingPrice(10000000L);
        extended.setMinimumIncrement(100000L);
        extended.setCurrentPrice(10500000L);
        extended.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)); // Jangan terlalu mepet biar gak bentrok scheduler
        extended.setStatus(AuctionStatus.EXTENDED);
        extended = auctionRepository.save(extended);
        addBid(extended, "buyer-4@mail.com", 10500000L);

        // CLOSED (Finished)
        Auction closed = createAuction("Signed Movie Poster", "listing-005", "seller-2@mail.com", 300000L, AuctionStatus.CLOSED, -1);
        addBid(closed, "buyer-1@mail.com", 450000L);
    }

    private void seedBulkData(int count) {
        for (int i = 1; i <= count; i++) {
            createAuction(
                "Generic Item #" + i, 
                "bulk-" + i, 
                "seller-" + (i % 3 + 1) + "@mail.com", 
                100000L + (i * 10000L), 
                (i % 10 == 0) ? AuctionStatus.CLOSED : AuctionStatus.ACTIVE,
                (i % 10 + 2)
            );
        }
    }

    private Auction createAuction(String title, String listingId, String sellerId, Long price, AuctionStatus status, int daysFromNow) {
        Auction a = new Auction();
        a.setTitle(title);
        a.setListingId(listingId);
        a.setSellerId(sellerId);
        a.setStartingPrice(price);
        a.setCurrentPrice(price);
        a.setMinimumIncrement(price / 10);
        a.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(daysFromNow));
        a.setStatus(status);
        return auctionRepository.save(a);
    }

    private Auction addBid(Auction a, String bidderId, Long amount) {
        Bid b = new Bid();
        b.setAuction(a);
        b.setBidderId(bidderId);
        b.setAmount(amount);
        bidRepository.save(b);
        
        a.setCurrentPrice(amount);
        return auctionRepository.save(a); // Return updated auction with new version
    }
}
