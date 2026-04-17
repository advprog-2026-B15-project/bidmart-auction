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
        // TODO: Konfirmasi dengan PJ Wallet:
        // 1. Apakah URL Endpoint-nya benar "/wallet/hold" ?
        //    (Bukan "/wallets/{userId}/hold" atau yang lain?)
        // 2. Apakah key JSON Body-nya persis memakai "userId", "auctionId", dan "amount" ?
        String endpoint = walletServiceUrl + "/wallet/hold"; 
        
        Map<String, Object> requestBody = Map.of(
                "userId", userId,
                "auctionId", auctionId,
                "amount", amount
        );

        restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                    (request, response) -> {
                    throw new IllegalStateException("Failed to hold balance: " + response.getStatusCode());
                })
                .toBodilessEntity();
    }
}
