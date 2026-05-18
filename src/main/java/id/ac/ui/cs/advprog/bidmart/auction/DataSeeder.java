package id.ac.ui.cs.advprog.bidmart.auction;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public void run(String... args) {
        if (auctionRepository.count() > 0) {
            log.info("[DataSeeder] Database already has data. Skipping fresh seeding to preserve your manual test data.");
            return;
        }

        log.info("[DataSeeder] Seeding fresh data scenarios...");

        // 1. DRAFT
        createAuction("Vintage Camera", "listing-001", "seller-1@mail.com", 500000L, AuctionStatus.DRAFT, 7);

        // 2. ACTIVE (No bids)
        createAuction("Gaming Laptop", "listing-002", "seller-2@mail.com", 15000000L, AuctionStatus.ACTIVE, 3);

        // 3. ACTIVE (With high activity)
        createAuction("Limited Edition Sneakers", "listing-003", "seller-3@mail.com", 2500000L, AuctionStatus.ACTIVE, 5);

        // 4. EXTENDED (Anti-sniping)
        createAuction("Antique Gold Coin", "listing-004", "seller-1@mail.com", 10500000L, AuctionStatus.EXTENDED, 0);

        // 5. CLOSED
        createAuction("Signed Movie Poster", "listing-005", "seller-2@mail.com", 450000L, AuctionStatus.CLOSED, -1);

        // 6. Bulk Data
        for (int i = 1; i <= 45; i++) {
            createAuction(
                "Generic Item #" + i, 
                "bulk-" + i, 
                "seller-" + (i % 3 + 1) + "@mail.com", 
                100000L + (i * 10000L), 
                (i % 10 == 0) ? AuctionStatus.CLOSED : AuctionStatus.ACTIVE,
                (i % 10 + 2)
            );
        }

        log.info("[DataSeeder] Done seeding database.");
    }

    private void createAuction(String title, String listingId, String sellerId, Long currentPrice, AuctionStatus status, int daysFromNow) {
        Auction a = new Auction();
        a.setTitle(title);
        a.setListingId(listingId);
        a.setSellerId(sellerId);
        a.setStartingPrice(currentPrice - (currentPrice / 10)); // Estimasikan starting price
        a.setCurrentPrice(currentPrice);
        a.setMinimumIncrement(50000L);
        a.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(daysFromNow).plusMinutes(10)); // Tambah buffer waktu
        a.setStatus(status);
        auctionRepository.save(a);
    }
}
