package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.RejectionReason;

/**
 * A Reactive Quiet Timeline provides maximum accuracy
 * as well as communicating errors with codes instead of throwing exceptions.
 * 
 * 
 */
public class ReactiveQuietTimeline extends Timeline {

    public ReactiveQuietTimeline(int maxEvents,
                                 long window,
                                 EventFilterer eventFilterer,
                                 Clock clock) 
    {
        super(maxEvents, window, eventFilterer, clock);
    }
    

    /**
     * Shortcut for applying user-defined filter on this timeline instance.
     *
     * @return if true, event must be rejected. if false, event can be added.
     */
    
    /**
     * Can add a new event?
     * 
     * @param nEvents
     * @return
     */
    @Override
    public boolean canAdd(int nEvents) {
        return hasSpaceForEvents(nEvents) && filterIn();
    }


    /**
     * Add a new event.
     * 
     * @return
     */
    @Override
    public boolean add() {
        if(wouldOverflow()) {
            setRejectionReason(RejectionReason.WOULD_OVERFLOW);
            return false;
        }
        
        if(filterOut()) { 
            setRejectionReason(RejectionReason.FILTERED_OUT);
            return false;
        }
        
        this.countInWindow.getAndIncrement();
        return true;
    }

    /**
     * Refreshes the current window period and resets counter.
     */
    @Override
    public void wakeup() {
        resetCountInWindow();
        this.windowStart.set(clock.getNow());
    }

    /**
     * Shortcut for applying user-defined filter on this timeline instance.
     *
     * @return if true, event can be added. if false, event must be rejected.
     */
    @Override
    protected boolean filterIn() {
        return eventFilterer.apply(this);
    }


}
