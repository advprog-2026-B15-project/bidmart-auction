package id.ac.ui.cs.advprog.bidmart.auction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import id.ac.ui.cs.advprog.bidmart.auction.dto.CreateAuctionRequest;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.service.AuctionService;
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
        auction.setCurrentBid(0L);
        auction.setStatus(AuctionStatus.DRAFT);
        auction.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));

        request = new CreateAuctionRequest();
        request.setListingId("listing-001");
        request.setTitle("Vintage Camera");
        request.setStartingPrice(500000L);
        request.setMinimumIncrement(50000L);
        request.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
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

    // ===== GET BY ID =====

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
                .thenThrow(new IllegalArgumentException("Auction tidak ditemukan"));

        mockMvc.perform(get("/api/auctions/invalid-id"))
                .andExpect(status().isNotFound());
    }

    // ===== CREATE =====

    @Test
    void testCreateAuctionSuccess() throws Exception {
        when(auctionService.create(any(CreateAuctionRequest.class), eq("seller-001")))
                .thenReturn(auction);

        mockMvc.perform(post("/api/auctions")
                        .header("X-Seller-Id", "seller-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("auction-101"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void testCreateAuctionMissingBody() throws Exception {
        mockMvc.perform(post("/api/auctions")
                        .header("X-Seller-Id", "seller-001"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateAuctionMissingSellerId() throws Exception {
        mockMvc.perform(post("/api/auctions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateAuctionInvalidRequest() throws Exception {
        request.setTitle(""); // NotBlank violation
        request.setStartingPrice(-1L); // Min violation

        mockMvc.perform(post("/api/auctions")
                        .header("X-Seller-Id", "seller-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ===== ACTIVATE =====

    @Test
    void testActivateAuctionSuccess() throws Exception {
        auction.setStatus(AuctionStatus.ACTIVE);
        when(auctionService.activate("auction-101", "seller-001")).thenReturn(auction);

        mockMvc.perform(patch("/api/auctions/auction-101/activate")
                        .header("X-Seller-Id", "seller-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void testActivateAuctionWrongSeller() throws Exception {
        when(auctionService.activate("auction-101", "seller-999"))
                .thenThrow(new IllegalStateException("Hanya seller pemilik yang bisa mengaktifkan lelang"));

        mockMvc.perform(patch("/api/auctions/auction-101/activate")
                        .header("X-Seller-Id", "seller-999"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testActivateAuctionNotDraft() throws Exception {
        when(auctionService.activate("auction-101", "seller-001"))
                .thenThrow(new IllegalStateException("Hanya auction berstatus DRAFT yang bisa diaktifkan"));

        mockMvc.perform(patch("/api/auctions/auction-101/activate")
                        .header("X-Seller-Id", "seller-001"))
                .andExpect(status().isBadRequest());
    }
}