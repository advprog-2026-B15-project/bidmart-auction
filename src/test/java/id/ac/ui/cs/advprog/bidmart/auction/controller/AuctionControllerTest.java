package id.ac.ui.cs.advprog.bidmart.auction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import id.ac.ui.cs.advprog.bidmart.auction.dto.CreateAuctionRequest;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.service.AuctionService;
import id.ac.ui.cs.advprog.bidmart.auction.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuctionController.class)
class AuctionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuctionService auctionService;

    @MockitoBean
    private JwtService jwtService; 

    private ObjectMapper objectMapper;
    private Auction auction;
    private CreateAuctionRequest request;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        auction = new Auction();
        auction.setId("auction-101");
        auction.setListingId("listing-001");
        auction.setSellerId("seller-001");
        auction.setTitle("Vintage Camera");
        auction.setStartingPrice(500000L);
        auction.setMinimumIncrement(50000L);
        auction.setCurrentPrice(0L);
        auction.setStatus(AuctionStatus.DRAFT);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));

        request = new CreateAuctionRequest();
        request.setListingId("listing-001");
        request.setTitle("Vintage Camera");
        request.setStartingPrice(500000L);
        request.setMinimumIncrement(50000L);
        request.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));

        lenient().when(jwtService.extractUserId(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token != null && token.contains("seller-001")) return "seller-001";
            if (token != null && token.contains("seller-999")) return "seller-999";
            if (token != null && token.contains("buyer-001")) return "buyer-001";
            return "user-id";
        });
    }

    @Test
    void testFindAllAuctions() throws Exception {
        when(auctionService.findAll()).thenReturn(Arrays.asList(auction));

        mockMvc.perform(get("/api/auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("auction-101"))
                .andExpect(jsonPath("$[0].title").value("Vintage Camera"));

        verify(auctionService, times(1)).findAll();
    }

    @Test
    void testFindByIdSuccess() throws Exception {
        when(auctionService.findById("auction-101")).thenReturn(auction);

        mockMvc.perform(get("/api/auctions/auction-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("auction-101"))
                .andExpect(jsonPath("$.title").value("Vintage Camera"));
    }

    @Test
    void testFindByIdNotFound() throws Exception {
        when(auctionService.findById("invalid-id"))
                .thenThrow(new NoSuchElementException("Auction not found"));

        mockMvc.perform(get("/api/auctions/invalid-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateAuctionSuccess() throws Exception {
        when(auctionService.create(any(CreateAuctionRequest.class), eq("seller-001")))
                .thenReturn(auction);

        mockMvc.perform(post("/api/auctions")
                        .header("Authorization", "Bearer seller-001")
                        .requestAttr("userId", "seller-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("auction-101"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void testCreateAuctionMissingBody() throws Exception {
        mockMvc.perform(post("/api/auctions")
                        .header("Authorization", "Bearer seller-001")
                        .requestAttr("userId", "seller-001"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateAuctionMissingSellerId() throws Exception {
        // tanpa Header Authorization, akan kena 401 dari Interceptor
        mockMvc.perform(post("/api/auctions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testCreateAuctionInvalidRequest() throws Exception {
        request.setTitle(""); 
        request.setStartingPrice(-1L);

        mockMvc.perform(post("/api/auctions")
                        .header("Authorization", "Bearer seller-001")
                        .requestAttr("userId", "seller-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateAuctionIllegalArgument() throws Exception {
        when(auctionService.create(any(), any()))
                .thenThrow(new IllegalArgumentException("Reserve price must be greater than starting price"));

        request.setReservePrice(100000L);
        mockMvc.perform(post("/api/auctions")
                        .header("Authorization", "Bearer seller-001")
                        .requestAttr("userId", "seller-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testActivateAuctionSuccess() throws Exception {
        auction.setStatus(AuctionStatus.ACTIVE);
        when(auctionService.activate("auction-101", "seller-001")).thenReturn(auction);

        mockMvc.perform(patch("/api/auctions/auction-101/activate")
                        .header("Authorization", "Bearer seller-001")
                        .requestAttr("userId", "seller-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void testActivateAuctionWrongSeller() throws Exception {
        when(auctionService.activate("auction-101", "seller-999"))
                .thenThrow(new IllegalStateException("Only the owner can activate this auction"));

        mockMvc.perform(patch("/api/auctions/auction-101/activate")
                        .header("Authorization", "Bearer seller-999")
                        .requestAttr("userId", "seller-999"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testActivateAuctionNotDraft() throws Exception {
        when(auctionService.activate("auction-101", "seller-001"))
                .thenThrow(new IllegalStateException("Only DRAFT auctions can be activated"));

        mockMvc.perform(patch("/api/auctions/auction-101/activate")
                        .header("Authorization", "Bearer seller-001")
                        .requestAttr("userId", "seller-001"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testHandleBadStateWith403Code() throws Exception {
        when(auctionService.activate("auction-101", "seller-001"))
                .thenThrow(new IllegalStateException("Failed to hold balance: 403 FORBIDDEN"));

        mockMvc.perform(patch("/api/auctions/auction-101/activate")
                        .header("Authorization", "Bearer seller-001")
                        .requestAttr("userId", "seller-001"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testHandleBadStateWith500Code() throws Exception {
        when(auctionService.activate("auction-101", "seller-001"))
                .thenThrow(new IllegalStateException("Failed to hold balance: 500 INTERNAL_SERVER_ERROR"));

        mockMvc.perform(patch("/api/auctions/auction-101/activate")
                        .header("Authorization", "Bearer seller-001")
                        .requestAttr("userId", "seller-001"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testPlaceBidSuccess() throws Exception {
        Bid bid = new Bid();
        bid.setId("bid-001");
        bid.setAuction(auction);
        bid.setBidderId("buyer-001");
        bid.setAmount(500000L);

        when(auctionService.placeBid("auction-101", "buyer-001", 500000L)).thenReturn(bid);

        mockMvc.perform(post("/api/auctions/auction-101/bids")
                        .header("Authorization", "Bearer buyer-001")
                        .requestAttr("userId", "buyer-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(500000));
    }

    @Test
    void testPlaceBidAuctionNotActive() throws Exception {
        when(auctionService.placeBid("auction-101", "buyer-001", 500000L))
                .thenThrow(new IllegalStateException("Auction is not active"));

        mockMvc.perform(post("/api/auctions/auction-101/bids")
                        .header("Authorization", "Bearer buyer-001")
                        .requestAttr("userId", "buyer-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPlaceBidAmountTooLow() throws Exception {
        when(auctionService.placeBid("auction-101", "buyer-001", 100L))
                .thenThrow(new IllegalArgumentException("Bid amount must be at least 500000"));

        mockMvc.perform(post("/api/auctions/auction-101/bids")
                        .header("Authorization", "Bearer buyer-001")
                        .requestAttr("userId", "buyer-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPlaceBidMissingBody() throws Exception {
        mockMvc.perform(post("/api/auctions/auction-101/bids")
                        .header("Authorization", "Bearer buyer-001")
                        .requestAttr("userId", "buyer-001"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetBidHistorySuccess() throws Exception {
        Bid bid1 = new Bid();
        bid1.setId("bid-001");
        bid1.setAuction(auction);
        bid1.setBidderId("buyer-001");
        bid1.setAmount(600000L);

        Bid bid2 = new Bid();
        bid2.setId("bid-002");
        bid2.setAuction(auction);
        bid2.setBidderId("buyer-002");
        bid2.setAmount(500000L);

        when(auctionService.getBidHistory("auction-101")).thenReturn(Arrays.asList(bid1, bid2));

        mockMvc.perform(get("/api/auctions/auction-101/bids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(600000))
                .andExpect(jsonPath("$[1].amount").value(500000));
    }

    @Test
    void testGetBidHistoryAuctionNotFound() throws Exception {
        when(auctionService.getBidHistory("invalid-id"))
                .thenThrow(new NoSuchElementException("Auction not found"));

        mockMvc.perform(get("/api/auctions/invalid-id/bids"))
                .andExpect(status().isNotFound());
    }
}
