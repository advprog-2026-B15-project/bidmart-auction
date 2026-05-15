CREATE INDEX idx_auctions_status_end_time ON auctions (status, end_time);
CREATE INDEX idx_auctions_status_current_price ON auctions (status, current_price);
