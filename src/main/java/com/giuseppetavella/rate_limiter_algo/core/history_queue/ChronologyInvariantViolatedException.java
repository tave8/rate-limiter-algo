package com.giuseppetavella.rate_limiter_algo.core.history_queue;

public class ChronologyInvariantViolatedException extends RuntimeException {
    public ChronologyInvariantViolatedException() {
        super("Chronology invariant violated.");
    }
}
