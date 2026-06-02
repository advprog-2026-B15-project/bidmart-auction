# BidMart Auction Monitoring Metrics Justification

The monitoring dashboard for the `bidmart-auction` service is configured using Grafana with metrics supplied by Prometheus. All panels in this dashboard are designed as *dashboard as code* (provisioning) so they can be readily available upon launching docker-compose.

This document explains the justifications for the selected metrics included in the dashboard.

## 1. Business Metrics

### Total Bids Placed (`auction_bids_placed_total`)
- **Justification**: This is the primary metric to measure the core activity of the auction application. By observing the total incoming bids, business and development teams can assess user engagement levels.
- **Implementation**: Retrieved using the *Micrometer Counter* `auction.bids.placed`, which increments every time the `placeBid` function is successfully executed.

### Active Auctions (`auction_active_count`)
- **Justification**: Indicates how many auctions are currently running (status `ACTIVE` or `EXTENDED`). This metric is crucial to determine the load capacity of the application at any given time and serves as an indicator of supply within the BidMart ecosystem.
- **Implementation**: Retrieved using a *Micrometer Gauge* that periodically reads the `countByStatusIn` function on the database repository.

### Bid Rate (Bids / Minute)
- **Justification**: Measures how fast bids are coming in per minute. Sudden spikes in the bid rate can indicate highly popular auctions, or conversely, a bot attack (spam bids).
- **Implementation**: Uses the `rate(auction_bids_placed_total[1m]) * 60` function in Prometheus.

## 2. Performance & Reliability Metrics

### API Apdex Score (T=0.5s)
- **Justification**: Apdex (Application Performance Index) is an industry standard for measuring user satisfaction regarding the response time of a service.
  - A score of 1.0 means all users are highly satisfied.
  - We set the satisfactory threshold (T) at **0.5 seconds (500ms)**.
  - This metric condenses complex latency data into a single, concise figure (0 to 1) that is easily understood by both management and technical stakeholders.
- **Implementation**: Calculated from `http_server_requests_seconds_bucket` using the standard Apdex formula: `(Satisfied + (Tolerating / 2)) / Total`. Where Satisfied = `le="0.5"` and Tolerating = `le="2.0"`.

### Bid Latency (p50, p95, p99)
- **Justification**: Averages (mean) often hide performance issues affecting a small subset of users. Percentiles (p95 and p99) are essential to observe worst-case scenarios. For instance, p99 = 2s means 1% of our users have to wait more than 2 seconds when placing a bid, which typically occurs due to distributed lock contention in heavily contested auctions.
- **Implementation**: Uses `histogram_quantile` on the `auction_bid_latency_seconds_bucket` metric, measured using the *Micrometer Timer* `bidLatencyTimer`.

### HTTP Request Rate
- **Justification**: Displays overall API traffic based on URI and HTTP Status Code. Useful for detecting sudden spikes in errors (e.g., surges in 4xx or 5xx statuses) in real-time.
- **Implementation**: Utilizes the auto-configuration feature from Spring Boot Actuator (`http_server_requests_seconds_count`).

## 3. Resource & Infrastructure Metrics

### JVM Heap Memory Usage
- **Justification**: Java applications are highly sensitive to Garbage Collection and Heap Memory usage. Memory leaks or Out Of Memory (OOM) errors can be predicted from this chart if the memory line continuously climbs toward the maximum limit (Max Heap) without ever dropping.
- **Implementation**: Utilizes the standard `jvm_memory_used_bytes` and `jvm_memory_max_bytes` metrics from the JVM.

### CPU Usage
- **Justification**: CPU usage hitting 100% will cause bottlenecks across all processing (especially WebSocket/SSE handling and auction locking). By monitoring Process CPU and System CPU, we can determine whether we need to scale out (adding instances / Blue-Green scaling).
- **Implementation**: Utilizes the standard `system_cpu_usage` and `process_cpu_usage` metrics.
