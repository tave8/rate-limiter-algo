package com.giuseppetavella.rate_limiter_algo;

public class ChronologyInvariantViolatedException extends RuntimeException {
    public ChronologyInvariantViolatedException() {
        super("Chronology invariant violated.");
    }
}
