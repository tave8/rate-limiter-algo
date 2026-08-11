package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.TooManyEventsInWindowException;

import java.sql.Time;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Timeline is the key component of this Rate Limiter implementation.
 * It only knows to run time logic and does not or care about scheduling logic.
 * 
 */
public class ReactiveTimeline extends Timeline {
    /**
     * 
     * 
     * @param maxEvents
     * @param manager
     * @param clock
     */
    public ReactiveTimeline(int maxEvents,
                            TimelineManager manager,
                            Clock clock) 
    {
        super(maxEvents, manager, clock);
    }
    

    /**
     * Shortcut for applying user-defined filter on this timeline instance.
     * 
     * @return if true, event can be added. if false, event must be rejected.
     */
    protected boolean filterIn() {
        return manager.getEventFilterer().apply(this);
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
    public boolean canAdd(int nEvents) {
        return hasSpaceForEvents(nEvents) && filterIn();
    }


    /**
     * Add a new event.
     * 
     * @return
     */
    public ReactiveTimeline add() {
        if(!hasSpaceForEvents(1)) {
            throw new TooManyEventsInWindowException(maxEvents);
        }
        
        if(filterOut()) { 
            throw new EventFilteredOutException(
                    maxEvents, 
                    countInWindow.get(), 
                    "Now: %d, Window start: %d, Diff: %d".formatted(
                            clock.getNow(), 
                            windowStart.get(), 
                            clock.getNow()-windowStart.get()
                    )
            );
        }
        
        this.countInWindow.getAndIncrement();
        return this;
    }
    
    /**
     * Refresh the current period.
     */
    public void refresh() {
        resetCountInWindow();
        this.windowStart.set(clock.getNow());
    }
    
    
    
}
