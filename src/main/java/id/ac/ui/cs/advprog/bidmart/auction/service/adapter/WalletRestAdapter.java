package id.ac.ui.cs.advprog.bidmart.auction.service.adapter;

import id.ac.ui.cs.advprog.bidmart.auction.service.port.HoldBalancePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
    public void holdBalance(String userId, String auctionId, Long amount) {
        executeWithRetry("/internal/wallet/hold", userId, auctionId, amount);
    }

    private void executeWithRetry(String path, String userId, String auctionId, Long amount) {
        int attempt = 0;
        while (true) {
            try {
                sendWalletRequest(path, userId, auctionId, amount);
                return;
            } catch (IllegalStateException e) {
                attempt++;
                if (isClientError(e) || attempt >= MAX_RETRIES) {
                    log.error("Wallet operation failed after {} attempt(s): {}", attempt, e.getMessage());
                    throw e;
                }
                log.warn("Wallet operation failed (attempt {}/{}), retrying in {}ms: {}",
                        attempt, MAX_RETRIES, RETRY_DELAY_MS, e.getMessage());
                sleep(RETRY_DELAY_MS);
            }
        }
    }

    private void sendWalletRequest(String path, String userId, String auctionId, Long amount) {
        String endpoint = walletServiceUrl + path;

        Map<String, Object> requestBody = Map.of(
                "userId", userId,
                "auctId", auctionId,
                "amount", amount
        );

        restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                    (request, response) -> {
                    throw new IllegalStateException("Client error (" + path + "): " + response.getStatusCode());
                })
                .onStatus(status -> status.is5xxServerError(),
                    (request, response) -> {
                    throw new IllegalStateException("Server error (" + path + "): " + response.getStatusCode());
                })
                .toBodilessEntity();
    }

    private boolean isClientError(IllegalStateException e) {
        return e.getMessage() != null && e.getMessage().startsWith("Client error");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }
}
