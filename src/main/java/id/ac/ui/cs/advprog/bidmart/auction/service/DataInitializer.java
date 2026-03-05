package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initData(AuctionRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                Auction a1 = new Auction();
                a1.setTitle("Vintage Camera");
                a1.setListingId("listing-001");
                a1.setSellerId("seller-001");
                a1.setStartingPrice(500000L);
                a1.setMinimumIncrement(50000L);
                a1.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
                repository.save(a1);

                Auction a2 = new Auction();
                a2.setTitle("Mechanical Keyboard");
                a2.setListingId("listing-002");
                a2.setSellerId("seller-001");
                a2.setStartingPrice(1200000L);
                a2.setMinimumIncrement(100000L);
                a2.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(3));
                repository.save(a2);
            }
        };
    }
}