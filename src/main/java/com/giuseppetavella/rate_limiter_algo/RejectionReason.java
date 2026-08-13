package com.giuseppetavella.rate_limiter_algo;

public enum RejectionReason {
    WOULD_OVERFLOW,
    FILTERED_OUT,
    BACKOFF
}
