package id.ac.ui.cs.advprog.bidmart.auction.service.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedLockTemplateTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Mock
    private LockCallback<String> callback;

    @InjectMocks
    private DistributedLockTemplate lockTemplate;

    private final String lockKey = "test-lock";

    @BeforeEach
    void setUp() {
        lenient().when(redissonClient.getLock(lockKey)).thenReturn(rLock);
    }

    @Test
    void executeWithLock_Success() throws Exception {
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(callback.doWithLock()).thenReturn("success");

        String result = lockTemplate.executeWithLock(lockKey, 5, 10, TimeUnit.SECONDS, callback);

        assertEquals("success", result);
        verify(rLock).tryLock(5, 10, TimeUnit.SECONDS);
        verify(callback).doWithLock();
        verify(rLock).unlock();
    }

    @Test
    void executeWithLock_CannotAcquireLock() throws Exception {
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> 
            lockTemplate.executeWithLock(lockKey, 5, 10, TimeUnit.SECONDS, callback)
        );

        assertTrue(exception.getMessage().contains("Could not acquire lock for key"));
        verify(callback, never()).doWithLock();
        verify(rLock, never()).unlock(); // not held
    }

    @Test
    void executeWithLock_CallbackThrowsException() throws Exception {
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(callback.doWithLock()).thenThrow(new IllegalArgumentException("Invalid state"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            lockTemplate.executeWithLock(lockKey, 5, 10, TimeUnit.SECONDS, callback)
        );

        assertTrue(exception.getMessage().contains("Invalid state"));
        verify(rLock).unlock(); // must still unlock
    }

    @Test
    void executeWithLock_CallbackThrowsNonRuntimeException() throws Exception {
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(callback.doWithLock()).thenThrow(new Exception("Checked exception"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            lockTemplate.executeWithLock(lockKey, 5, 10, TimeUnit.SECONDS, callback)
        );

        assertTrue(exception.getMessage().contains("Error executing inside lock"));
        verify(rLock).unlock();
    }

    @Test
    void executeWithLock_InterruptedException() throws Exception {
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("Interrupted"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> 
            lockTemplate.executeWithLock(lockKey, 5, 10, TimeUnit.SECONDS, callback)
        );

        assertTrue(exception.getMessage().contains("Thread interrupted while waiting for lock"));
        assertTrue(Thread.currentThread().isInterrupted());
        
        // clean up interrupt flag for other tests
        Thread.interrupted();
    }
}
