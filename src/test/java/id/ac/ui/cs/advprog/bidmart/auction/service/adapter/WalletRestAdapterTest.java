package id.ac.ui.cs.advprog.bidmart.auction.service.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@RestClientTest(WalletRestAdapter.class)
@TestPropertySource(properties = {"bidmart.wallet-service.url=http://localhost:8080"})
class WalletRestAdapterTest {

    @Autowired
    private WalletRestAdapter walletRestAdapter;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void testHoldBalanceSuccess() {
        server.expect(requestTo("http://localhost:8080/internal/wallet/hold"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value("user-001"))
                .andExpect(jsonPath("$.auctId").value("auction-001"))
                .andExpect(jsonPath("$.amount").value(500000))
                .andRespond(withSuccess());

        walletRestAdapter.holdBalance("user-001", "auction-001", 500000L);
        server.verify();
    }

    @Test
    void testHoldBalanceError4xx() {
        server.expect(requestTo("http://localhost:8080/internal/wallet/hold"))
                .andRespond(withBadRequest());

        assertThrows(IllegalStateException.class, () -> 
            walletRestAdapter.holdBalance("user-001", "auction-001", 500000L)
        );
        server.verify();
    }

    @Test
    void testHoldBalanceError5xx() {
        server.expect(requestTo("http://localhost:8080/internal/wallet/hold"))
                .andRespond(withServerError());

        assertThrows(IllegalStateException.class, () -> 
            walletRestAdapter.holdBalance("user-001", "auction-001", 500000L)
        );
        server.verify();
    }
}
