package com.giuseppetavella.rate_limiter_algo;

public class EventsAddedTooFastException extends RuntimeException {
    public EventsAddedTooFastException(int maxEvents, long currEvents) {
        super("Events were added too fast, you must wait. "
                +"Max events: %d, Curr events: %d".formatted(maxEvents, currEvents));
    }
}
