ALTER TABLE auctions RENAME COLUMN current_bid TO current_price;
ALTER TABLE bids RENAME COLUMN bidder_username TO bidder_id;
