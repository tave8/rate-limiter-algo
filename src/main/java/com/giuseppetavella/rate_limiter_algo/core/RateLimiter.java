package com.giuseppetavella.rate_limiter_algo.core;

public interface RateLimiter {
    boolean add();
    boolean canAdd();
    RejectionReason getRejectionReason();
}
