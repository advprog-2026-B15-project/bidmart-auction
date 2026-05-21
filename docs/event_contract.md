# Event Contract (Message Broker)

The Auction Service uses **RabbitMQ** (CloudAMQP) to send messages to other microservices. This is called an *Event-Driven* system. We send messages in the background so the auction doesn't need to wait for other services.

> **Exchange Name:** `auction.events.exchange` (Topic Exchange)

## 1. `BidPlacedEvent`
This is sent **every time** a user successfully places a bid.
- **Routing Key:** `auction.bid.placed`
- **Who Listens to This:** 
  - **Notification/Booking Service:** To tell the previous highest bidder they were outbid.
- **Message Content:**
  ```json
  {
    "bidId": "bid-999",
    "auctionId": "auc-1001",
    "listingId": "lst-5001",
    "sellerUserId": "usr-2001",
    "bidderUserId": "buyer-001",
    "previousBidderUserId": "buyer-000",
    "bidAmount": 15500000,
    "itemName": "Mechanical Keyboard"
  }
  ```

## 2. `AuctionClosedEvent`
This event is published **only when an auction cycle ends without a winner** (the status transitions to `UNSOLD`). This happens if no one placed a bid at all, or if the highest bid did not meet the seller's secret minimum price (reserve price).
- **Routing Key:** `auction.state.closed`
- **Who Listens to This:** 
  - **Notification/Booking Service:** To log the closure and send notifications to all participants that the auction ended without a winner.
  - **Catalog Service:** To change the item's status back to "available" so it can be re-listed.
  - **Wallet Service:** To release the held money back to all the bidders listed in the `allBidderIds` array.
- **Message Content:**
  ```json
  {
    "auctionId": "auc-1001",
    "listingId": "lst-5001",
    "sellerUserId": "usr-2001",
    "closedAt": "2026-05-03T09:20:00Z",
    "allBidderIds": [
      "usr-3002",
      "usr-3003"
    ]
  }
  ```

## 3. `WinnerDeterminedEvent`
This is sent after the system checks a closed auction and confirms that the final price is high enough to win.
- **Routing Key:** `auction.winner.determined`
- **Who Listens to This:** 
    - **Booking Service** (to automatically create an order/booking and send WIN notifications to the participants).
- **Message Content:**
  ```json
  {
    "auctionId": "auc-1001",
    "listingId": "lst-5001",
    "sellerUserId": "usr-2001",
    "winnerUserId": "usr-3001",
    "finalPrice": 1750000,
    "currency": "IDR",
    "itemName": "Mechanical Keyboard",
    "quantity": 1,
    "loserUserIds": [
      "usr-3002",
      "usr-3003"
    ]
  }
  ```
