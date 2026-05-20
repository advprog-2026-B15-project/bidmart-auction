# Design Principles & Clean Code

This project follows **SOLID principles** and **Clean Code** rules. This makes the code easier to read, fix, and upgrade in the future.

## 1. SOLID Principles Implementation

### S - Single Responsibility Principle
Every class in the code has only one specific job.
- `AuctionController` only handles incoming web requests and responses.
- `AuctionService` only handles the main business rules of the auction.
- `SseEmitterService` only handles the live updates (real-time connections).
- `AuctionClosingScheduler` only handles checking for expired auctions in the background.

### O - Open/Closed Principle
The system is built so you can add new features without breaking existing code.
- **Example:** We use the `BidValidationStrategy`. If we want to add a new rule tomorrow (for example: "VIP users can bid lower amounts"), we just create a new validation class. We don't have to change the core `AuctionService` code.

### D - Dependency Inversion Principle
- **Example:** The `AuctionService` does not talk directly to the real `RabbitMQ` or the real HTTP web client. Instead, it talks to simple "Interfaces" (`AuctionEventPort`, `HoldBalancePort`). The real connection code is kept completely separate in "Adapter" classes.

## 2. Design Patterns Used

1. **DTO (Data Transfer Object) Pattern:** 
   We separate the large database objects (`Bid`, `Auction`) from the simple data we send to the users (`BidResponse`). This makes the response smaller, hides private data, and makes our Redis cache much faster.
2. **Strategy Pattern:**
   As mentioned above, we use this to organize the different rules for validating a new bid.
3. **Adapter Pattern:**
   We hide the messy code for RabbitMQ and the Wallet HTTP calls inside Adapter classes. If we ever want to switch from RabbitMQ to Kafka, we only need to change the Adapter, not the main auction logic.
4. **Observer/Pub-Sub Pattern:**
   We use RabbitMQ and Spring Events to tell other parts of the system when something happens, without making them directly depend on each other.
