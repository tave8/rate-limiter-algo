package com.giuseppetavella.rate_limiter_algo;

public interface RateLimiter<T> {
    int getMaxEvents();
    long getWindow();
    boolean canAdd(int nEvents);
    T add();
}
