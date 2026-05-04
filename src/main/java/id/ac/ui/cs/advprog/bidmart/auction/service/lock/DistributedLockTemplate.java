package id.ac.ui.cs.advprog.bidmart.auction.service.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockTemplate {

    private final RedissonClient redissonClient;

    public <T> T executeWithLock(
            String lockKey, long waitTime, long leaseTime, TimeUnit unit, LockCallback<T> callback) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLocked = false;
        try {
            isLocked = lock.tryLock(waitTime, leaseTime, unit);
            if (!isLocked) {
                throw new IllegalStateException("Could not acquire lock for key: " + lockKey);
            }
            log.debug("Lock acquired for key: {}", lockKey);
            return callback.doWithLock();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted while waiting for lock: " + lockKey, e);
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Error executing inside lock for key: " + lockKey, e);
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released for key: {}", lockKey);
            }
        }
    }
}
