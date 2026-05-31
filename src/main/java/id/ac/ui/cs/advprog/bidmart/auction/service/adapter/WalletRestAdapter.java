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
    private final String holdPath;
    private final String releasePath;
    private final String convertPath;

    static final int MAX_RETRIES = 3;
    static final long RETRY_DELAY_MS = 1000;

    public WalletRestAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${bidmart.wallet-service.url}") String walletServiceUrl,
            @Value("${bidmart.wallet-service.hold-path:/internal/wallet/hold}") String holdPath,
            @Value("${bidmart.wallet-service.release-path:/internal/wallet/release}") String releasePath,
            @Value("${bidmart.wallet-service.convert-path:/internal/wallet/convert}") String convertPath) {
        this.restClient = restClientBuilder.build();
        this.walletServiceUrl = walletServiceUrl;
        this.holdPath = holdPath;
        this.releasePath = releasePath;
        this.convertPath = convertPath;
    }

    @Override
    @Retryable(
        retryFor = { IllegalStateException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void holdBalance(String userId, String auctionId, Long amount) {
        String endpoint = walletServiceUrl + holdPath;
        String idempotencyKey = auctionId + "-" + userId + "-" + amount;

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
                .onStatus(org.springframework.http.HttpStatusCode::is4xxClientError,
                    (request, response) -> {
                    throw new IllegalArgumentException("Client error (" + holdPath + "): " + response.getStatusCode());
                })
                .onStatus(org.springframework.http.HttpStatusCode::is5xxServerError,
                    (request, response) -> {
                    throw new IllegalStateException("Server error (" + holdPath + "): " + response.getStatusCode());
                })
                .toBodilessEntity();
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
        String endpoint = walletServiceUrl + releasePath;
        String idempotencyKey = auctionId + "-" + userId + "-" + amount + "-release";

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
                .onStatus(org.springframework.http.HttpStatusCode::is4xxClientError,
                    (request, response) -> {
                    throw new IllegalArgumentException("Client error (" + releasePath + "): " + response.getStatusCode());
                })
                .onStatus(org.springframework.http.HttpStatusCode::is5xxServerError,
                    (request, response) -> {
                    throw new IllegalStateException("Server error (" + releasePath + "): " + response.getStatusCode());
                })
                .toBodilessEntity();
    }

    protected void simulateLatencyForProfiling() {
        log.info("[MOCK-WALLET] Bypassing external REST call to Wallet.");

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Profiling latency simulation interrupted", e);
        }
    }

    @Recover
    public void releaseBalanceFallback(IllegalStateException e, String userId, String auctionId, Long amount) {
        log.error("Failed to release balance for user={} auction={} after retries: {}",
                userId, auctionId, e.getMessage());
        throw e;
    }

    @Override
    @Retryable(
        retryFor = { IllegalStateException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void convertBalance(String userId, String auctionId, Long amount) {
        String endpoint = walletServiceUrl + convertPath;
        String idempotencyKey = auctionId + "-" + userId + "-" + amount + "-convert";

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
                .onStatus(org.springframework.http.HttpStatusCode::is4xxClientError,
                    (request, response) -> {
                    throw new IllegalArgumentException("Client error (" + convertPath + "): " + response.getStatusCode());
                })
                .onStatus(org.springframework.http.HttpStatusCode::is5xxServerError,
                    (request, response) -> {
                    throw new IllegalStateException("Server error (" + convertPath + "): " + response.getStatusCode());
                })
                .toBodilessEntity();
    }

    @Recover
    public void convertBalanceFallback(IllegalStateException e, String userId, String auctionId, Long amount) {
        log.error("Failed to convert balance for user={} auction={} after retries: {}",
                userId, auctionId, e.getMessage());
        throw e;
    }
}
