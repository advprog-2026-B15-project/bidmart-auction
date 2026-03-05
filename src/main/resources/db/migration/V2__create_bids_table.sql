CREATE TABLE bids (
                      id VARCHAR(36) PRIMARY KEY,
                      auction_id VARCHAR(36) NOT NULL REFERENCES auctions(id),
                      bidder_username VARCHAR(100) NOT NULL,
                      amount BIGINT NOT NULL CHECK (amount > 0),
                      created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_bids_auction_id ON bids (auction_id);