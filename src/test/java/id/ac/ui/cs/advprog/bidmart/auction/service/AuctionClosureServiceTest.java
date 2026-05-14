package id.ac.ui.cs.advprog.bidmart.auction.service;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.DistributedLockTemplate;
import id.ac.ui.cs.advprog.bidmart.auction.service.lock.LockCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionClosureServiceTest {

    @Mock
    private DistributedLockTemplate lockTemplate;

    @Mock
    private AuctionClosureDatabaseService auctionClosureDatabaseService;

    @InjectMocks
    private AuctionClosureService auctionClosureService;

    private Auction auction;
    private OffsetDateTime now;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        now = OffsetDateTime.now();

        auction = new Auction();
        auction.setId("auc-1");
        auction.setStatus(AuctionStatus.ACTIVE);

        when(lockTemplate.executeWithLock(
                startsWith("auction-lock-"), anyLong(), anyLong(), any(TimeUnit.class), any(LockCallback.class)))
            .thenAnswer(inv -> ((LockCallback<Void>) inv.getArgument(4)).doWithLock());
    }

    @Test
    void processAuctionClosure_acquiresLockAndDelegatesToDatabaseService() {
        auctionClosureService.processAuctionClosure(auction, now);

        verify(lockTemplate).executeWithLock(
                eq("auction-lock-auc-1"), anyLong(), anyLong(), any(TimeUnit.class), any(LockCallback.class));

        verify(auctionClosureDatabaseService).closeAuction("auc-1", now);
    }

    @Test
    void processAuctionClosure_usesAuctionIdAsLockKey() {
        Auction anotherAuction = new Auction();
        anotherAuction.setId("auc-99");
        anotherAuction.setStatus(AuctionStatus.EXTENDED);

        auctionClosureService.processAuctionClosure(anotherAuction, now);

        verify(lockTemplate).executeWithLock(
                eq("auction-lock-auc-99"), anyLong(), anyLong(), any(TimeUnit.class), any(LockCallback.class));
        verify(auctionClosureDatabaseService).closeAuction("auc-99", now);
    }

    @Test
    void processAuctionClosure_databaseServiceThrows_exceptionPropagates() {
        doThrow(new RuntimeException("DB error"))
                .when(auctionClosureDatabaseService).closeAuction(anyString(), any());

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> auctionClosureService.processAuctionClosure(auction, now));
    }
}
