package com.giuseppetavella.rate_limiter_algo;

public interface RateLimiter {
    boolean add();
    boolean canAdd();
    RejectionReason getRejectionReason();
}
