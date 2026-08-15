package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.timeline.RateLimiterState;

public abstract class AbstractRateLimiter implements RateLimiter {
    protected final int maxEvents;
    protected final long window;
    protected Clock clock;
    
    
    protected RejectionReason rejectionReason;

    protected RateLimiterState state;
    
    public AbstractRateLimiter(int maxEvents,
                               long window,
                               Clock clock)
                                    throws IllegalArgumentException
    {
        if(window < 50) {
            throw new IllegalArgumentException("Time window must be >= 100, got %s.".formatted(window));
        }
        if(maxEvents < 0) {
            throw new IllegalArgumentException("Max events must be >= 0, got %s.".formatted(maxEvents));
        }
        this.maxEvents = maxEvents;
        this.window = window;
        this.clock = clock == null ? defaultClockSupplier() : clock;
        this.state = RateLimiterState.NEW;

    }

    public abstract boolean canAdd(int nEvents);
    
    public boolean canAdd() {
        return canAdd(1);
    };
    
    public abstract boolean add();
    
    
    public int getMaxEvents() {
        return maxEvents;
    }
    
    public long getWindow() {
        return window;
    }
    
    public abstract long getCountInWindow();

    /**
     *      * Note specific to Timeline algorithm: Because of how the Timeline algorithm works,
     *      * artificial time mechanism might not work as expected, 
     *      * because this algorithm needs actual time to run the
     *      * timelines on a schedule, and that cannot be faked.
     *      * 
     *      * @param delay
     *      * @return
     *      
     * @param delay
     * @return
     */
    public AbstractRateLimiter after(long delay) {
        this.clock.after(delay);
        return this;
    }

    public Clock getClock() {
        return clock;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }
    
    public void setRejectionReason(RejectionReason reason) {
        this.rejectionReason = reason;
    }

    protected Clock defaultClockSupplier() {
        return new ClockImpl();
    }

    public RateLimiterState getState() {
        return state;
    }

    public void setState(RateLimiterState state) {
        this.state = state;
    }


    @Override
    public String toString() {
        return "AbstractRateLimiter{" +
                "maxEvents=" + maxEvents +
                ", window=" + window +
                '}';
    }
}
