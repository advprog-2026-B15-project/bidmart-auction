package id.ac.ui.cs.advprog.bidmart.auction.service.adapter;

import id.ac.ui.cs.advprog.bidmart.auction.service.port.HoldBalancePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class WalletRestAdapter implements HoldBalancePort {

    private final RestClient restClient;
    private final String walletServiceUrl;

    static final int MAX_RETRIES = 3;
    static final long RETRY_DELAY_MS = 1000;

    public WalletRestAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${bidmart.wallet-service.url}") String walletServiceUrl) {
        this.restClient = restClientBuilder.build();
        this.walletServiceUrl = walletServiceUrl;
    }

    @Override
    @Retryable(
        retryFor = { IllegalStateException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void holdBalance(String userId, String auctionId, Long amount) {
        String path = "/internal/wallet/hold";
        String endpoint = walletServiceUrl + path;
        String idempotencyKey = auctionId + "-" + userId;

        Map<String, Object> requestBody = Map.of(
                "userId", userId,
                "auctId", auctionId,
                "amount", amount,
                "idempotencyKey", idempotencyKey
        );

        restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                    (request, response) -> {
                    throw new IllegalArgumentException("Client error (" + path + "): " + response.getStatusCode());
                })
                .onStatus(status -> status.is5xxServerError(),
                    (request, response) -> {
                    throw new IllegalStateException("Server error (" + path + "): " + response.getStatusCode());
                })
                .toBodilessEntity();

        // simulateLatencyForProfiling();
    }

    @Recover
    public void holdBalanceFallback(IllegalStateException e, String userId, String auctionId, Long amount) {
        log.error("Failed to hold balance for user={} auction={} after retries: {}", 
                userId, auctionId, e.getMessage());
        throw e;
    }

    @Override
    @Retryable(
        retryFor = { IllegalStateException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void releaseBalance(String userId, String auctionId, Long amount) {
        String path = "/internal/wallet/release";
        String endpoint = walletServiceUrl + path;
        String idempotencyKey = auctionId + "-" + userId + "-release";

        Map<String, Object> requestBody = Map.of(
                "userId", userId,
                "auctId", auctionId,
                "amount", amount,
                "idempotencyKey", idempotencyKey
        );

        restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                    (request, response) -> {
                    throw new IllegalArgumentException("Client error (" + path + "): " + response.getStatusCode());
                })
                .onStatus(status -> status.is5xxServerError(),
                    (request, response) -> {
                    throw new IllegalStateException("Server error (" + path + "): " + response.getStatusCode());
                })
                .toBodilessEntity();

        // simulateLatencyForProfiling();
    }

    protected void simulateLatencyForProfiling() {
        log.info("[MOCK-WALLET] Bypassing external REST call to Wallet.");

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Profiling latency simulation interrupted", e);
        }
    }

    @Recover
    public void releaseBalanceFallback(IllegalStateException e, String userId, String auctionId, Long amount) {
        log.error("Failed to release balance for user={} auction={} after retries: {}", 
                userId, auctionId, e.getMessage());
        throw e;
    }
}
