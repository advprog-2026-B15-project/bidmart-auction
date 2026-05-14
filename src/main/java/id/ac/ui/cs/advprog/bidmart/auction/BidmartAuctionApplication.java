package id.ac.ui.cs.advprog.bidmart.auction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableScheduling
@EnableRetry
public class BidmartAuctionApplication {

    public static void main(String[] args) {
        SpringApplication.run(BidmartAuctionApplication.class, args);
    }

}
