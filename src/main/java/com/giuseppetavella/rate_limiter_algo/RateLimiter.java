package com.giuseppetavella.rate_limiter_algo;

public interface RateLimiter {
    boolean canAdd(int nEvents);
    boolean canAdd();
    RateLimiter add();
    int getMaxEvents();
    long getWindow();
    long getCountInWindow();
    RateLimiter after(long delay);
}
