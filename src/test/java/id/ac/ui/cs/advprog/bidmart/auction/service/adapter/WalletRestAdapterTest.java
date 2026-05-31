package id.ac.ui.cs.advprog.bidmart.auction.service.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.HoldBalancePort;

@RestClientTest(WalletRestAdapter.class)
@TestPropertySource(properties = {"bidmart.wallet-service.url=http://localhost:8080"})
@Import(WalletRestAdapterTest.RetryConfig.class)
class WalletRestAdapterTest {

    @TestConfiguration
    @EnableRetry
    static class RetryConfig {}

    @Autowired
    private HoldBalancePort walletRestAdapter;

    @Autowired
    private MockRestServiceServer server;

    // ===== holdBalance tests =====

    @Test
    void testHoldBalanceSuccess() {
        server.expect(requestTo("http://localhost:8080/internal/wallet/hold"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value("user-001"))
                .andExpect(jsonPath("$.auctId").value("auction-001"))
                .andExpect(jsonPath("$.amount").value(500000))
                .andExpect(jsonPath("$.idempotencyKey").value("auction-001-user-001-500000"))
                .andRespond(withSuccess());

        walletRestAdapter.holdBalance("user-001", "auction-001", 500000L);
        server.verify();
    }

    @Test
    void testHoldBalanceError4xx_noRetry() {
        server.expect(times(1), requestTo("http://localhost:8080/internal/wallet/hold"))
                .andRespond(withBadRequest());

        assertThrows(Exception.class, () ->
            walletRestAdapter.holdBalance("user-001", "auction-001", 500000L)
        );
        server.verify();
    }

    @Test
    void testHoldBalanceError5xx_retriesAndFails() {
        server.expect(times(3), requestTo("http://localhost:8080/internal/wallet/hold"))
                .andRespond(withServerError());

        assertThrows(IllegalStateException.class, () ->
            walletRestAdapter.holdBalance("user-001", "auction-001", 500000L)
        );
        server.verify();
    }

    @Test
    void testHoldBalanceError5xx_retriesAndSucceeds() {
        server.expect(requestTo("http://localhost:8080/internal/wallet/hold"))
                .andRespond(withServerError());
        server.expect(requestTo("http://localhost:8080/internal/wallet/hold"))
                .andRespond(withSuccess());

        assertDoesNotThrow(() ->
            walletRestAdapter.holdBalance("user-001", "auction-001", 500000L)
        );
        server.verify();
    }

    @Test
    void testReleaseBalanceSuccess() {
        server.expect(requestTo("http://localhost:8080/internal/wallet/release"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value("user-001"))
                .andExpect(jsonPath("$.auctId").value("auction-001"))
                .andExpect(jsonPath("$.amount").value(500000))
                .andExpect(jsonPath("$.idempotencyKey").value("auction-001-user-001-500000-release"))
                .andRespond(withSuccess());

        walletRestAdapter.releaseBalance("user-001", "auction-001", 500000L);
        server.verify();
    }

    @Test
    void testReleaseBalanceError4xx_noRetry() {
        server.expect(times(1), requestTo("http://localhost:8080/internal/wallet/release"))
                .andRespond(withBadRequest());

        assertThrows(Exception.class, () ->
            walletRestAdapter.releaseBalance("user-001", "auction-001", 500000L)
        );
        server.verify();
    }

    @Test
    void testReleaseBalanceError5xx_retriesAndFails() {
        server.expect(times(3), requestTo("http://localhost:8080/internal/wallet/release"))
                .andRespond(withServerError());

        assertThrows(IllegalStateException.class, () ->
            walletRestAdapter.releaseBalance("user-001", "auction-001", 500000L)
        );
        server.verify();
    }

    @Test
    void testReleaseBalanceError5xx_retriesAndSucceeds() {
        server.expect(requestTo("http://localhost:8080/internal/wallet/release"))
                .andRespond(withServerError());
        server.expect(requestTo("http://localhost:8080/internal/wallet/release"))
                .andRespond(withSuccess());

        assertDoesNotThrow(() ->
            walletRestAdapter.releaseBalance("user-001", "auction-001", 500000L)
        );
        server.verify();
    }

    @Test
    void testConvertBalanceSuccess() {
        server.expect(requestTo("http://localhost:8080/internal/wallet/convert"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value("user-001"))
                .andExpect(jsonPath("$.auctId").value("auction-001"))
                .andExpect(jsonPath("$.amount").value(500000))
                .andExpect(jsonPath("$.idempotencyKey").value("auction-001-user-001-500000-convert"))
                .andRespond(withSuccess());

        walletRestAdapter.convertBalance("user-001", "auction-001", 500000L);
        server.verify();
    }

    @Test
    void testConvertBalanceError4xx_noRetry() {
        server.expect(times(1), requestTo("http://localhost:8080/internal/wallet/convert"))
                .andRespond(withBadRequest());

        assertThrows(Exception.class, () ->
            walletRestAdapter.convertBalance("user-001", "auction-001", 500000L)
        );
        server.verify();
    }

    @Test
    void testConvertBalanceError5xx_retriesAndFails() {
        server.expect(times(3), requestTo("http://localhost:8080/internal/wallet/convert"))
                .andRespond(withServerError());

        assertThrows(IllegalStateException.class, () ->
            walletRestAdapter.convertBalance("user-001", "auction-001", 500000L)
        );
        server.verify();
    }

    @Test
    void testConvertBalanceError5xx_retriesAndSucceeds() {
        server.expect(requestTo("http://localhost:8080/internal/wallet/convert"))
                .andRespond(withServerError());
        server.expect(requestTo("http://localhost:8080/internal/wallet/convert"))
                .andRespond(withSuccess());

        assertDoesNotThrow(() ->
            walletRestAdapter.convertBalance("user-001", "auction-001", 500000L)
        );
        server.verify();
    }
    
    @Test
    void testSimulateLatencyForProfiling() {
        WalletRestAdapter adapter = new WalletRestAdapter(org.springframework.web.client.RestClient.builder(), "", "", "", "");
        assertDoesNotThrow(() -> adapter.simulateLatencyForProfiling());
        
        Thread.currentThread().interrupt();
        assertThrows(IllegalStateException.class, () -> adapter.simulateLatencyForProfiling());
    }
}
