package com.giuseppetavella.rate_limiter_algo;

public interface RateLimiter {
    boolean canAdd(int nEvents);
    RateLimiter add();
    int getMaxEvents();
    long getWindow();
}
