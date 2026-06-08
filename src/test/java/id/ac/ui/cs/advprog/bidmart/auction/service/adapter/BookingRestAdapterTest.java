package id.ac.ui.cs.advprog.bidmart.auction.service.adapter;

import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class BookingRestAdapterTest {

    private BookingRestAdapter adapter;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        adapter = new BookingRestAdapter(
                builder,
                "http://booking-service",
                "/internal/bookings/winner-determined",
                "/internal/bookings/pay-by-auction"
        );
        // We need to inject the MockRestServiceServer to the restClient inside the adapter
        // But since the adapter creates the RestClient from the builder immediately in the constructor,
        // we can bind the server to the builder.
        mockServer = MockRestServiceServer.bindTo(builder).build();
        
        // Re-instantiate adapter with the builder that now has the MockRestServiceServer bound
        adapter = new BookingRestAdapter(
                builder,
                "http://booking-service",
                "/internal/bookings/winner-determined",
                "/internal/bookings/pay-by-auction"
        );
    }

    @Test
    void testCreateBookingFromWinnerSuccess() {
        WinnerDeterminedEvent event = new WinnerDeterminedEvent();
        
        mockServer.expect(ExpectedCount.once(), requestTo("http://booking-service/internal/bookings/winner-determined"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        assertDoesNotThrow(() -> adapter.createBookingFromWinner(event));
        mockServer.verify();
    }

    @Test
    void testCreateBookingFromWinnerConflict() {
        WinnerDeterminedEvent event = new WinnerDeterminedEvent();
        event.setEventId("event-123");
        
        mockServer.expect(ExpectedCount.once(), requestTo("http://booking-service/internal/bookings/winner-determined"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertDoesNotThrow(() -> adapter.createBookingFromWinner(event));
        mockServer.verify();
    }

    @Test
    void testCreateBookingFromWinnerClientError() {
        WinnerDeterminedEvent event = new WinnerDeterminedEvent();
        
        mockServer.expect(ExpectedCount.once(), requestTo("http://booking-service/internal/bookings/winner-determined"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThrows(IllegalArgumentException.class, () -> adapter.createBookingFromWinner(event));
        mockServer.verify();
    }

    @Test
    void testCreateBookingFromWinnerServerError() {
        WinnerDeterminedEvent event = new WinnerDeterminedEvent();
        
        mockServer.expect(ExpectedCount.once(), requestTo("http://booking-service/internal/bookings/winner-determined"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(IllegalStateException.class, () -> adapter.createBookingFromWinner(event));
        mockServer.verify();
    }

    @Test
    void testMarkBookingPaidSuccess() {
        mockServer.expect(ExpectedCount.once(), requestTo("http://booking-service/internal/bookings/pay-by-auction/auction-123"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess());

        assertDoesNotThrow(() -> adapter.markBookingPaid("auction-123"));
        mockServer.verify();
    }

    @Test
    void testMarkBookingPaidClientError() {
        mockServer.expect(ExpectedCount.once(), requestTo("http://booking-service/internal/bookings/pay-by-auction/auction-123"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // It catches exceptions and logs them
        assertDoesNotThrow(() -> adapter.markBookingPaid("auction-123"));
        mockServer.verify();
    }

    @Test
    void testMarkBookingPaidServerError() {
        mockServer.expect(ExpectedCount.once(), requestTo("http://booking-service/internal/bookings/pay-by-auction/auction-123"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // It catches exceptions and logs them
        assertDoesNotThrow(() -> adapter.markBookingPaid("auction-123"));
        mockServer.verify();
    }

    @Test
    void testCreateBookingFromWinnerFallback() {
        WinnerDeterminedEvent event = new WinnerDeterminedEvent();
        WinnerDeterminedEvent.Payload payload = WinnerDeterminedEvent.Payload.builder()
                .auctionId("auction-123")
                .winnerUserId("user-123")
                .build();
        event.setPayload(payload);
        
        IllegalStateException exception = new IllegalStateException("Simulated Server Error");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, 
            () -> adapter.createBookingFromWinnerFallback(exception, event));
        
        org.junit.jupiter.api.Assertions.assertEquals("Simulated Server Error", thrown.getMessage());
    }
}
