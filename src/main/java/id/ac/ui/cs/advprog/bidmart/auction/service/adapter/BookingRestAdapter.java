package id.ac.ui.cs.advprog.bidmart.auction.service.adapter;

import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.BookingCreationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class BookingRestAdapter implements BookingCreationPort {

    private final RestClient restClient;
    private final String bookingServiceUrl;
    private final String winnerDeterminedPath;
    private final String payByAuctionPath;

    public BookingRestAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${bidmart.booking-service.url}") String bookingServiceUrl,
            @Value("${bidmart.booking-service.winner-determined-path:/internal/bookings/winner-determined}")
            String winnerDeterminedPath,
            @Value("${bidmart.booking-service.pay-by-auction-path:/internal/bookings/pay-by-auction}")
            String payByAuctionPath
    ) {
        this.restClient = restClientBuilder.build();
        this.bookingServiceUrl = bookingServiceUrl;
        this.winnerDeterminedPath = winnerDeterminedPath;
        this.payByAuctionPath = payByAuctionPath;
    }

    @Override
    @Retryable(
            retryFor = {IllegalStateException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void createBookingFromWinner(WinnerDeterminedEvent event) {
        String endpoint = bookingServiceUrl + winnerDeterminedPath;

        restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(event)
                .retrieve()
                .onStatus(status -> status.value() == 409, (request, response) -> {
                    log.info(
                            "Booking already exists for winner event {}. Treating as idempotent success.",
                            event.getEventId()
                    );
                })
                .onStatus(org.springframework.http.HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new IllegalArgumentException(
                            "Client error (" + winnerDeterminedPath + "): " + response.getStatusCode()
                    );
                })
                .onStatus(org.springframework.http.HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new IllegalStateException(
                            "Server error (" + winnerDeterminedPath + "): " + response.getStatusCode()
                    );
                })
                .toBodilessEntity();
    }

    @Override
    public void markBookingPaid(String auctionId) {
        String endpoint = bookingServiceUrl + payByAuctionPath + "/" + auctionId;
        try {
            restClient.patch()
                    .uri(endpoint)
                    .retrieve()
                    .onStatus(org.springframework.http.HttpStatusCode::is4xxClientError, (request, response) -> {
                        log.warn("markBookingPaid 4xx for auction={}: {}", auctionId, response.getStatusCode());
                    })
                    .onStatus(org.springframework.http.HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new IllegalStateException("Server error marking booking paid: " + response.getStatusCode());
                    })
                    .toBodilessEntity();
            log.info("Marked booking as PAID via REST for auction={}", auctionId);
        } catch (Exception e) {
            log.error("Failed to mark booking as PAID for auction={}: {}", auctionId, e.getMessage());
        }
    }

    @Recover
    public void createBookingFromWinnerFallback(IllegalStateException e, WinnerDeterminedEvent event) {
        log.error(
                "Failed to create booking for auction={} winner={} after retries: {}",
                event.getPayload().getAuctionId(),
                event.getPayload().getWinnerUserId(),
                e.getMessage()
        );
        throw e;
    }
}
