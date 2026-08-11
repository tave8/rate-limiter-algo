package com.giuseppetavella.rate_limiter_algo;

public interface ClockModifier {
    ClockModifier after(long delay);
    long getCumulativeDelay();
    long getActualNow();
    long getNow();
}
