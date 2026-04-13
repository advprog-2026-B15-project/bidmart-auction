package id.ac.ui.cs.advprog.bidmart.auction.service.strategy;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import org.springframework.stereotype.Component;

@Component
public class AmountValidationStrategy implements BidValidationStrategy {
    @Override
    public void validate(Auction auction, Long bidAmount) {
        Long minimumRequired;

        if (auction.getCurrentBid() != null && auction.getCurrentBid() > 0) {
            Long increment = auction.getMinimumIncrement() != null ? auction.getMinimumIncrement() : 1L;
            minimumRequired = auction.getCurrentBid() + increment;
        } else {
            minimumRequired = auction.getStartingPrice();
        }
        
        if (bidAmount < minimumRequired) {
            throw new IllegalArgumentException("Bid amount must be at least " + minimumRequired);
        }
    }
}