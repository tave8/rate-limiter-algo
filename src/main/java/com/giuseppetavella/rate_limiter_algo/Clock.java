package com.giuseppetavella.rate_limiter_algo;


public class Clock implements ClockModifier {
    private long cumulativeDelay;
    
    /**
     * Returns milliseconds.  
     * 
     * @return
     */
    @Override
    public long getActualNow() {
        return System.currentTimeMillis();
    }

    @Override
    public long getNow() {
        return getActualNow() + cumulativeDelay;
    }

    @Override
    public ClockModifier after(long delay) {
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
}
