package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initData(AuctionRepository repository) {
        // data dummy
        return args -> {
            if (repository.count() == 0) {
                Auction a1 = new Auction();
                a1.setTitle("Vintage Camera");
                a1.setCurrentBid(500000.0);
                repository.save(a1);

                Auction a2 = new Auction();
                a2.setTitle("Mechanical Keyboard");
                a2.setCurrentBid(1200000.0);
                repository.save(a2);
            }
        };
    }
}
