package com.giuseppetavella.rate_limiter_algo;

public class TooManyEventsInWindowException extends EventRejectedException {
    
    public TooManyEventsInWindowException(int maxEvents, long currCount) {
        super("Too many events in window, must wait. Max events: %d, Curr count: %d".formatted(maxEvents, currCount));
    }
      
    public TooManyEventsInWindowException(int maxEvents) {
        super("Too many events in window, must wait. Max events is: " + maxEvents);
    }
    
    
}
