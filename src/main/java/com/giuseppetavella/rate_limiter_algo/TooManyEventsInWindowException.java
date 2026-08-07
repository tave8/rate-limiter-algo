package com.giuseppetavella.rate_limiter_algo;

public class TooManyEventsInWindowException extends RuntimeException {
    public TooManyEventsInWindowException(int maxEvents) {
        super("Too many events in window, must wait. Max events is: " + maxEvents);
    }
    
  public TooManyEventsInWindowException() {
    super("Too many events in window, must wait.");
  }
    
}
