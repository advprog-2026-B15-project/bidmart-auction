# Design Principles & Clean Code

This project follows **SOLID principles** and **Clean Code** rules. This makes the code easier to read, fix, and upgrade in the future.

## 1. SOLID Principles Implementation

### S - Single Responsibility Principle
Every class in the code has only one specific job.
- `AuctionController` only handles incoming web requests and responses.
- `AuctionService` only handles the main business rules of the auction. It does not handle caching infrastructure; instead, it delegates cache updates to `AuctionCacheUpdaterListener`.
```java
@Component
public class AuctionCacheUpdaterListener {
    @EventListener
    public void handleLocalBidSaved(LocalBidSavedEvent event) {
        var auctionCache = cacheManager.getCache("auction");
        if (auctionCache != null) {
            auctionCache.put(event.getAuction().getId(), event.getAuction());
        }
    }
}
```
- `SseEmitterService` only handles the live updates (real-time connections).
- `AuctionClosingScheduler` only handles checking for expired auctions in the background.
```

### O - Open/Closed Principle
The system is built so you can add new features without breaking existing code.
- **Example:** We use the `BidValidationStrategy`. If we want to add a new rule tomorrow (for example: "VIP users can bid lower amounts"), we just create a new validation class. We don't have to change the core `AuctionService` code.
```java
public interface BidValidationStrategy {
    void validate(Auction auction, Long bidAmount);
}

@Component
public class AmountValidationStrategy implements BidValidationStrategy {
    @Override
    public void validate(Auction auction, Long bidAmount) {
        if (bidAmount < (auction.getCurrentPrice() + auction.getMinimumIncrement())) {
            throw new IllegalArgumentException("Bid amount is too low");
        }
    }
}
```

### L - Liskov Substitution Principle
Any child class or interface implementation must be swappable with its parent without breaking the application.
- **Example:** The `BidValidationStrategy` interface is implemented by multiple classes. The `AuctionService` simply loops through them and calls `.validate()`. It does not need to know which specific strategy it is executing, and all strategies strictly obey the contract.
```java
for (BidValidationStrategy strategy : validationStrategies) {
    strategy.validate(preliminaryAuction, amount);
}
```

### I - Interface Segregation Principle
A class should not be forced to implement or depend on methods it does not use. We split large interfaces into smaller, specific ones.
- **Example:** Instead of creating one giant `ExternalSystemPort` that handles everything, we segregated the interfaces into focused ports:
```java
// HoldBalancePort.java
public interface HoldBalancePort {
    void holdBalance(String userId, String auctionId, Long amount);
    void releaseBalance(String userId, String auctionId, Long amount);
}

// AuctionEventPort.java
public interface AuctionEventPort {
    void publishBidPlaced(BidPlacedEvent event);
    void publishWinnerDetermined(WinnerDeterminedEvent event);
    void publishAuctionClosed(AuctionClosedEvent event);
}
```
Because of this segregation, the `AuctionClosureDatabaseService` only injects `AuctionEventPort` and is completely unaware of Wallet operations, keeping its dependencies lean.

### D - Dependency Inversion Principle
- **Example:** The `AuctionService` does not talk directly to the real `RabbitMQ` or the real HTTP web client. Instead, it talks to simple "Interfaces" (`AuctionEventPort`, `HoldBalancePort`). The real connection code is kept completely separate in "Adapter" classes.
```java
@Service
public class AuctionService {
    private final HoldBalancePort holdBalancePort;
    private final AuctionEventPort auctionEventPort;

    public AuctionService(HoldBalancePort holdBalancePort, AuctionEventPort auctionEventPort) {
        this.holdBalancePort = holdBalancePort;
        this.auctionEventPort = auctionEventPort;
    }
}
```

## 2. Design Patterns Used

1. **DTO (Data Transfer Object) Pattern:** 
   We separate the large database objects (`Bid`, `Auction`) from the simple data we send to the users (`BidResponse`). This makes the response smaller, hides private data, and makes Redis cache much faster.
2. **Strategy Pattern:**
   As mentioned above, we use this to organize the different rules for validating a new bid.
3. **Adapter Pattern:**
   We hide the messy code for RabbitMQ and the Wallet HTTP calls inside Adapter classes. If we ever want to switch from RabbitMQ to Kafka, we only need to change the Adapter, not the main auction logic.
4. **Observer Pattern:**
   We use Spring's `ApplicationEventPublisher` to decouple components. For example, caching updates are triggered via `LocalBidSavedEvent`. Crucially, we use `@TransactionalEventListener` to ensure RabbitMQ events are only published *after* the database commit succeeds, keeping transaction boundaries perfectly clean.
5. **Domain-Driven Design:**
   Instead of using an "Anemic Domain Model" where entities are just data bags, we moved business logic directly into the entities. For example, the Anti-Sniping rule is handled by the `Auction` entity itself (`auction.applyAntiSnipingRule()`), ensuring the domain model strictly controls its own state mutations without duplicating logic in the Service layer.
