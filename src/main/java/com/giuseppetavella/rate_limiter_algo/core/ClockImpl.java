package com.giuseppetavella.rate_limiter_algo.core;

/**
 * An implementation of a Clock.
 * This implementation uses {@code System.currentTimeMillis()}
 * to get the milliseconds from epoch for the physical now. 
 * You are free to create your own implementations 
 * with other mechanisms.
 * 
 */
public class ClockImpl implements Clock {
    private long cumulativeDelay;
    private long lastElapsedAt;

    public ClockImpl() {
        this.lastElapsedAt = 0;
    }

    @Override
    public long getActualNow() {
        return System.currentTimeMillis();
    }

    @Override
    public long getNow() {
        return getActualNow() + cumulativeDelay;
    }

    @Override
    public ClockImpl after(long delay) {
        if(delay < 0) {
            throw new IllegalArgumentException("Delay must be >= 0");
        }
        this.cumulativeDelay += delay;
        return this;
    }

    @Override
    public long getCumulativeDelay() {
        return cumulativeDelay;
    }

    /**
     * Get the elapsed time since last time this same method was called.
     * 
     * @return
     */
    @Override
    public long measureElapsed() {
        if(lastElapsedAt == 0) { // First call
            this.lastElapsedAt = getNow();
            return 0;
        }
        long res = getNow() - lastElapsedAt; 
        this.lastElapsedAt = getNow();
        return res;
    }

    // @Override
    // public long measureElapsedAbs() {
    //     if(lastElapsedAt == 0) { // First call
    //         this.lastElapsedAt = getNow();
    //         return 0;
    //     }
    //     long res = getNow() - lastElapsedAt;
    //     this.lastElapsedAt = getNow();
    //     return res;
    // }
    
}
