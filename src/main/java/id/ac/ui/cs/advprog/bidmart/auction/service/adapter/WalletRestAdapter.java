package id.ac.ui.cs.advprog.bidmart.auction.service.adapter;

import id.ac.ui.cs.advprog.bidmart.auction.service.port.HoldBalancePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class WalletRestAdapter implements HoldBalancePort {

    private final RestClient restClient;
    private final String walletServiceUrl;

    public WalletRestAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${bidmart.wallet-service.url}") String walletServiceUrl) {
        this.restClient = restClientBuilder.build();
        this.walletServiceUrl = walletServiceUrl;
    }

    @Override
    public void holdBalance(String userId, String auctionId, Long amount) {
        sendWalletRequest("/internal/wallet/hold", userId, auctionId, amount);
    }

    private void sendWalletRequest(String path, String userId, String auctionId, Long amount) {
        String endpoint = walletServiceUrl + path;
        
        Map<String, Object> requestBody = Map.of(
                "userId", userId,
                "auctId", auctionId, // Sesuai dengan field di HoldRequest Wallet
                "amount", amount
        );

        restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                    (request, response) -> {
                    throw new IllegalStateException("Failed wallet operation (" + path + "): " + response.getStatusCode());
                })
                .toBodilessEntity();
    }
}
