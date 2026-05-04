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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionClosingSchedulerTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private DistributedLockTemplate lockTemplate;

    @Mock
    private AuctionEventPort auctionEventPort;

    @InjectMocks
    private AuctionClosingScheduler scheduler;

    @Captor
    private ArgumentCaptor<WinnerDeterminedEvent> winnerDeterminedCaptor;

    @Captor
    private ArgumentCaptor<AuctionClosedEvent> auctionClosedCaptor;

    private Auction auction1;
    private Bid highestBid;

    @BeforeEach
    void setUp() {
        auction1 = new Auction();
        auction1.setId("auc-1");
        auction1.setTitle("Test Item");
        auction1.setStatus(AuctionStatus.ACTIVE);
        auction1.setReservePrice(100L);
        auction1.setSellerId("seller-1");
        auction1.setListingId("listing-1");

        highestBid = new Bid();
        highestBid.setId("bid-1");
        highestBid.setAuction(auction1);
        highestBid.setBidderId("buyer-1");
        highestBid.setAmount(150L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void closeExpiredAuctions_WonScenario() throws Exception {
        when(lockTemplate.executeWithLock(eq("auction-scheduler-lock"), eq(0L), eq(30L), eq(TimeUnit.SECONDS), any(LockCallback.class)))
            .thenAnswer(invocation -> {
                LockCallback<Void> callback = invocation.getArgument(4);
                return callback.doWithLock();
            });

        when(lockTemplate.executeWithLock(eq("auction-lock-auc-1"), eq(5L), eq(10L), eq(TimeUnit.SECONDS), any(LockCallback.class)))
            .thenAnswer(invocation -> {
                LockCallback<Void> callback = invocation.getArgument(4);
                return callback.doWithLock();
            });

        when(auctionRepository.findExpiredByStatuses(anyList(), any(OffsetDateTime.class)))
                .thenReturn(Collections.singletonList(auction1));
        
        when(auctionRepository.findById("auc-1")).thenReturn(Optional.of(auction1));
        when(bidRepository.findHighestBid("auc-1")).thenReturn(Optional.of(highestBid));
        when(bidRepository.findDistinctLoserBidderIds("auc-1", "buyer-1")).thenReturn(Arrays.asList("buyer-2"));

        scheduler.closeExpiredAuctions();

        assertEquals(AuctionStatus.WON, auction1.getStatus());
        verify(auctionRepository).save(auction1);
        verify(auctionEventPort).publishWinnerDetermined(winnerDeterminedCaptor.capture());

        WinnerDeterminedEvent event = winnerDeterminedCaptor.getValue();
        assertEquals("auc-1", event.getPayload().getAuctionId());
        assertEquals("buyer-1", event.getPayload().getWinnerUserId());
        assertEquals(150L, event.getPayload().getFinalPrice());
        assertEquals(1, event.getPayload().getLoserUserIds().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void closeExpiredAuctions_UnsoldScenario() throws Exception {
        auction1.setReservePrice(500L);

        when(lockTemplate.executeWithLock(eq("auction-scheduler-lock"), anyLong(), anyLong(), any(TimeUnit.class), any(LockCallback.class)))
            .thenAnswer(invocation -> ((LockCallback<Void>) invocation.getArgument(4)).doWithLock());

        when(lockTemplate.executeWithLock(eq("auction-lock-auc-1"), anyLong(), anyLong(), any(TimeUnit.class), any(LockCallback.class)))
            .thenAnswer(invocation -> ((LockCallback<Void>) invocation.getArgument(4)).doWithLock());

        when(auctionRepository.findExpiredByStatuses(anyList(), any(OffsetDateTime.class)))
                .thenReturn(Collections.singletonList(auction1));
        
        when(auctionRepository.findById("auc-1")).thenReturn(Optional.of(auction1));
        when(bidRepository.findHighestBid("auc-1")).thenReturn(Optional.of(highestBid));
        when(bidRepository.findDistinctBidderIdsByAuctionId("auc-1")).thenReturn(Arrays.asList("buyer-1"));

        scheduler.closeExpiredAuctions();

        assertEquals(AuctionStatus.UNSOLD, auction1.getStatus());
        verify(auctionRepository).save(auction1);
        verify(auctionEventPort).publishAuctionClosed(auctionClosedCaptor.capture());

        AuctionClosedEvent event = auctionClosedCaptor.getValue();
        assertEquals("auc-1", event.getPayload().getAuctionId());
        assertEquals(1, event.getPayload().getAllBidderIds().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void closeExpiredAuctions_AlreadyProcessed() throws Exception {
        auction1.setStatus(AuctionStatus.WON); // already WON

        when(lockTemplate.executeWithLock(eq("auction-scheduler-lock"), anyLong(), anyLong(), any(TimeUnit.class), any(LockCallback.class)))
            .thenAnswer(invocation -> ((LockCallback<Void>) invocation.getArgument(4)).doWithLock());

        when(lockTemplate.executeWithLock(eq("auction-lock-auc-1"), anyLong(), anyLong(), any(TimeUnit.class), any(LockCallback.class)))
            .thenAnswer(invocation -> ((LockCallback<Void>) invocation.getArgument(4)).doWithLock());

        Auction staleAuction = new Auction();
        staleAuction.setId("auc-1");
        staleAuction.setStatus(AuctionStatus.ACTIVE);

        when(auctionRepository.findExpiredByStatuses(anyList(), any(OffsetDateTime.class)))
                .thenReturn(Collections.singletonList(staleAuction));
        
        when(auctionRepository.findById("auc-1")).thenReturn(Optional.of(auction1));

        scheduler.closeExpiredAuctions();

        verify(bidRepository, never()).findHighestBid(anyString());
        verify(auctionRepository, never()).save(any(Auction.class));
    }
}
