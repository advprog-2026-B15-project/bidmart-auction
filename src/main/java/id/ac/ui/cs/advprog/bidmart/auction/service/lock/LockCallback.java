package id.ac.ui.cs.advprog.bidmart.auction.service.lock;

public interface LockCallback<T> {
    T doWithLock() throws Exception;
}
