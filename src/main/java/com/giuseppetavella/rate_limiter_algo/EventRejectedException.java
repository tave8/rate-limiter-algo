package com.giuseppetavella.rate_limiter_algo;

/**
 * Use this for the most generic exception to throw when
 * rate limiter has rejected a new event.
 */
public class EventRejectedException extends RuntimeException {
    public EventRejectedException(RateLimiter limiter) {
        super("Event rejected by Rate Limiter. Reason: %s".formatted(limiter.getRejectionReason())
        );
    }
    
    public EventRejectedException(String customMsg) {
        super(customMsg);
    }
}
