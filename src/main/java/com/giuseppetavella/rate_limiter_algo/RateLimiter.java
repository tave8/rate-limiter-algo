package com.giuseppetavella.rate_limiter_algo;

public abstract class RateLimiter {
    protected final int maxEvents;
    protected final long window;
    protected final Clock clock;
    
    protected RejectionReason rejectionReason;

    public RateLimiter(int maxEvents,
                       long window,
                       Clock clock)
                            throws IllegalArgumentException
    {
        if(window < 100) {
            throw new IllegalArgumentException("Time window must be >= 100.");
        }
        if(maxEvents < 0) {
            throw new IllegalArgumentException("Max events must be >= 0.");
        }
        this.maxEvents = maxEvents;
        this.window = window;
        this.clock = clock;
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
    public RateLimiter after(long delay) {
        this.clock.after(delay);
        return this;
    }
    
    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }
    
    protected void setRejectionReason(RejectionReason reason) {
        this.rejectionReason = reason;
    }
}
