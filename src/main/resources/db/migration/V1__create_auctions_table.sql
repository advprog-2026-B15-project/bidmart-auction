CREATE TABLE auctions (
                          id VARCHAR(36) PRIMARY KEY,
                          listing_id VARCHAR(36) NOT NULL,
                          seller_id VARCHAR(36) NOT NULL,
                          title VARCHAR(255) NOT NULL,
                          starting_price BIGINT NOT NULL CHECK (starting_price > 0),
                          reserve_price BIGINT CHECK (reserve_price > 0),
                          minimum_increment BIGINT NOT NULL DEFAULT 1000 CHECK (minimum_increment > 0),
                          current_bid BIGINT NOT NULL DEFAULT 0 CHECK (current_bid >= 0),
                          status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
                          end_time TIMESTAMP WITH TIME ZONE NOT NULL,
                          created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

                          CONSTRAINT reserve_gt_starting
                              CHECK (reserve_price IS NULL OR reserve_price > starting_price)
);

CREATE INDEX idx_auctions_seller_id ON auctions (seller_id);
CREATE INDEX idx_auctions_status ON auctions (status);
CREATE INDEX idx_auctions_listing_id ON auctions (listing_id);