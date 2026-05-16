package id.ac.ui.cs.advprog.bidmart.auction.controller;

import id.ac.ui.cs.advprog.bidmart.auction.service.AuctionService;
import id.ac.ui.cs.advprog.bidmart.auction.service.JwtService;
import id.ac.ui.cs.advprog.bidmart.auction.service.SseEmitterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuctionController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuctionService auctionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private SseEmitterService sseEmitterService;

    @Test
    void handleNotFound_returns404WithJsonBody() throws Exception {
        when(auctionService.findById("bad-id"))
                .thenThrow(new NoSuchElementException("Auction not found"));

        mockMvc.perform(get("/api/auctions/bad-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Auction not found"));
    }

    @Test
    void handleBadRequest_returns400WithJsonBody() throws Exception {
        when(auctionService.findById("bad-id"))
                .thenThrow(new IllegalArgumentException("Invalid argument"));

        mockMvc.perform(get("/api/auctions/bad-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid argument"));
    }

    @Test
    void handleBadState_ownerMessage_returns403() throws Exception {
        when(auctionService.findById("auction-x"))
                .thenThrow(new IllegalStateException("Only the owner can activate this auction"));

        mockMvc.perform(get("/api/auctions/auction-x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void handleBadState_wallet403_returnsForbidden() throws Exception {
        when(auctionService.findById("auction-x"))
                .thenThrow(new IllegalStateException("Failed to hold balance: 403 FORBIDDEN"));

        mockMvc.perform(get("/api/auctions/auction-x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Wallet error: Forbidden. Check your balance or permissions."));
    }

    @Test
    void handleBadState_wallet500_returnsInternalServerError() throws Exception {
        when(auctionService.findById("auction-x"))
                .thenThrow(new IllegalStateException("Failed to hold balance: 500 INTERNAL_SERVER_ERROR"));

        mockMvc.perform(get("/api/auctions/auction-x"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Wallet service is currently unavailable."));
    }

    @Test
    void handleBadState_genericMessage_returns400() throws Exception {
        when(auctionService.findById("auction-x"))
                .thenThrow(new IllegalStateException("Some generic bad state"));

        mockMvc.perform(get("/api/auctions/auction-x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Some generic bad state"));
    }

    @Test
    void handleGenericException_returns500() throws Exception {
        when(auctionService.findById("auction-x"))
                .thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(get("/api/auctions/auction-x"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
    }
}
