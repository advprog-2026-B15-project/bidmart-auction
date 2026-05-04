package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.dto.AuctionClosedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.dto.WinnerDeterminedEvent;
import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.model.Bid;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.repository.BidRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.LockCallback;
import id.ac.ui.cs.advprog.bidmart.auction.service.port.AuctionEventPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionClosureServiceTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private DistributedLockTemplate lockTemplate;

    @Mock
    private AuctionEventPort auctionEventPort;

    @InjectMocks
    private AuctionClosureService auctionClosureService;

    @Captor
    private ArgumentCaptor<WinnerDeterminedEvent> winnerDeterminedCaptor;

    @Captor
    private ArgumentCaptor<AuctionClosedEvent> auctionClosedCaptor;

    private Auction auction;
    private Bid highestBid;
    private OffsetDateTime now;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        now = OffsetDateTime.now();

        auction = new Auction();
        auction.setId("auc-1");
        auction.setTitle("Test Item");
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setReservePrice(100L);
        auction.setSellerId("seller-1");
        auction.setListingId("listing-1");

        highestBid = new Bid();
        highestBid.setId("bid-1");
        highestBid.setAuction(auction);
        highestBid.setBidderId("buyer-1");
        highestBid.setAmount(150L);

        when(lockTemplate.executeWithLock(
                startsWith("auction-lock-"), anyLong(), anyLong(), any(TimeUnit.class), any(LockCallback.class)))
            .thenAnswer(inv -> ((LockCallback<Void>) inv.getArgument(4)).doWithLock());
    }

    @Test
    void processAuctionClosure_wonScenario_publishesWinnerDeterminedEvent() {
        when(auctionRepository.findById("auc-1")).thenReturn(Optional.of(auction));
        when(bidRepository.findHighestBid("auc-1")).thenReturn(Optional.of(highestBid));
        when(bidRepository.findDistinctLoserBidderIds("auc-1", "buyer-1"))
                .thenReturn(Arrays.asList("buyer-2", "buyer-3"));

        auctionClosureService.processAuctionClosure(auction, now);

        assertEquals(AuctionStatus.WON, auction.getStatus());
        verify(auctionRepository).save(auction);
        verify(auctionEventPort).publishWinnerDetermined(winnerDeterminedCaptor.capture());
        verifyNoMoreInteractions(auctionEventPort);

        WinnerDeterminedEvent event = winnerDeterminedCaptor.getValue();
        assertEquals("auc-1", event.getPayload().getAuctionId());
        assertEquals("buyer-1", event.getPayload().getWinnerUserId());
        assertEquals(150L, event.getPayload().getFinalPrice());
        assertEquals(2, event.getPayload().getLoserUserIds().size());
        assertEquals("WinnerDetermined", event.getEventType());
        assertEquals("bidmart-auction", event.getSource());
    }

    @Test
    void processAuctionClosure_unsoldScenario_publishesAuctionClosedEvent() {
        auction.setReservePrice(500L); // bid 150 < reserve 500

        when(auctionRepository.findById("auc-1")).thenReturn(Optional.of(auction));
        when(bidRepository.findHighestBid("auc-1")).thenReturn(Optional.of(highestBid));
        when(bidRepository.findDistinctBidderIdsByAuctionId("auc-1"))
                .thenReturn(Collections.singletonList("buyer-1"));

        auctionClosureService.processAuctionClosure(auction, now);

        assertEquals(AuctionStatus.UNSOLD, auction.getStatus());
        verify(auctionRepository).save(auction);
        verify(auctionEventPort).publishAuctionClosed(auctionClosedCaptor.capture());
        verifyNoMoreInteractions(auctionEventPort);

        AuctionClosedEvent event = auctionClosedCaptor.getValue();
        assertEquals("auc-1", event.getPayload().getAuctionId());
        assertEquals(1, event.getPayload().getAllBidderIds().size());
        assertEquals("AuctionClosed", event.getEventType());
    }

    @Test
    void processAuctionClosure_noBids_closesAsUnsold() {
        when(auctionRepository.findById("auc-1")).thenReturn(Optional.of(auction));
        when(bidRepository.findHighestBid("auc-1")).thenReturn(Optional.empty());
        when(bidRepository.findDistinctBidderIdsByAuctionId("auc-1")).thenReturn(Collections.emptyList());

        auctionClosureService.processAuctionClosure(auction, now);

        assertEquals(AuctionStatus.UNSOLD, auction.getStatus());
        verify(auctionEventPort).publishAuctionClosed(any(AuctionClosedEvent.class));
    }

    @Test
    void processAuctionClosure_noReservePrice_wonWithAnyBid() {
        auction.setReservePrice(null); // tanpa reserve price, bid apapun menang

        when(auctionRepository.findById("auc-1")).thenReturn(Optional.of(auction));
        when(bidRepository.findHighestBid("auc-1")).thenReturn(Optional.of(highestBid));
        when(bidRepository.findDistinctLoserBidderIds("auc-1", "buyer-1")).thenReturn(Collections.emptyList());

        auctionClosureService.processAuctionClosure(auction, now);

        assertEquals(AuctionStatus.WON, auction.getStatus());
        verify(auctionEventPort).publishWinnerDetermined(any(WinnerDeterminedEvent.class));
    }

    @Test
    void processAuctionClosure_alreadyProcessed_skipsProcessing() {
        Auction alreadyWon = new Auction();
        alreadyWon.setId("auc-1");
        alreadyWon.setStatus(AuctionStatus.WON); // sudah diproses

        when(auctionRepository.findById("auc-1")).thenReturn(Optional.of(alreadyWon));

        auctionClosureService.processAuctionClosure(auction, now);

        verify(auctionRepository, never()).save(any());
        verifyNoInteractions(auctionEventPort);
        verifyNoInteractions(bidRepository);
    }

    @Test
    void processAuctionClosure_auctionNotFound_skipsProcessing() {
        when(auctionRepository.findById("auc-1")).thenReturn(Optional.empty());

        auctionClosureService.processAuctionClosure(auction, now);

        verify(auctionRepository, never()).save(any());
        verifyNoInteractions(auctionEventPort);
        verifyNoInteractions(bidRepository);
    }
}
