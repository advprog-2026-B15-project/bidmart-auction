package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.repository.AuctionRepository;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.LockCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionClosingSchedulerTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private DistributedLockTemplate lockTemplate;

    @Mock
    private AuctionClosureService auctionClosureService;

    @InjectMocks
    private AuctionClosingScheduler scheduler;

    private Auction auction1;
    private Auction auction2;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        auction1 = new Auction();
        auction1.setId("auc-1");
        auction1.setStatus(AuctionStatus.ACTIVE);

        auction2 = new Auction();
        auction2.setId("auc-2");
        auction2.setStatus(AuctionStatus.EXTENDED);

        // Default stub: langsung jalankan callback lock global
        when(lockTemplate.executeWithLock(
                eq("auction-scheduler-lock"), anyLong(), anyLong(), any(TimeUnit.class), any(LockCallback.class)))
            .thenAnswer(inv -> ((LockCallback<Void>) inv.getArgument(4)).doWithLock());
    }

    @Test
    void closeExpiredAuctions_delegatesToClosureServiceForEachAuction() throws Exception {
        when(auctionRepository.findExpiredByStatuses(anyList(), any(OffsetDateTime.class)))
                .thenReturn(Arrays.asList(auction1, auction2));

        scheduler.closeExpiredAuctions();

        verify(auctionClosureService).processAuctionClosure(eq(auction1), any(OffsetDateTime.class));
        verify(auctionClosureService).processAuctionClosure(eq(auction2), any(OffsetDateTime.class));
        verifyNoMoreInteractions(auctionClosureService);
    }

    @Test
    void closeExpiredAuctions_noExpiredAuctions_doesNotDelegate() throws Exception {
        when(auctionRepository.findExpiredByStatuses(anyList(), any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());

        scheduler.closeExpiredAuctions();

        verifyNoInteractions(auctionClosureService);
    }

    @Test
    void closeExpiredAuctions_closureServiceThrows_continuesProcessingOthers() throws Exception {
        when(auctionRepository.findExpiredByStatuses(anyList(), any(OffsetDateTime.class)))
                .thenReturn(Arrays.asList(auction1, auction2));

        doThrow(new RuntimeException("DB error"))
                .when(auctionClosureService).processAuctionClosure(eq(auction1), any(OffsetDateTime.class));

        scheduler.closeExpiredAuctions();

        verify(auctionClosureService).processAuctionClosure(eq(auction2), any(OffsetDateTime.class));
    }
}
