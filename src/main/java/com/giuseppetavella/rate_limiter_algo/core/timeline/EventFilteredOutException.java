package com.giuseppetavella.rate_limiter_algo.core.timeline;

public class EventFilteredOutException extends RuntimeException {
    public EventFilteredOutException(int maxEvents, long currEvents, String details) {
        super("Event filtered out, you must wait. "
                +"Max events: %d, Curr events: %d, Details: %s".formatted(maxEvents, currEvents, details));
    }
}
