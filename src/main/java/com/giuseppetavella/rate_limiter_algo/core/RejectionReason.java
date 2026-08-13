package com.giuseppetavella.rate_limiter_algo.core;

public enum RejectionReason {
    WOULD_OVERFLOW,
    FILTERED_OUT,
    BACKOFF
}
