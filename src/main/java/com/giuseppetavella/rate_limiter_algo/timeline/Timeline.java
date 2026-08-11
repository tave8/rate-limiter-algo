package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.ClockModifier;
import com.giuseppetavella.rate_limiter_algo.TooManyEventsInWindowException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The Timeline is the key component of this Rate Limiter implementation.
 * It only knows to run time logic and does not or care about scheduling logic.
 * 
 */
public class Timeline {
    private final int maxEvents;
    private final AtomicLong countInWindow;
    private final TimelineManager manager;
    private final AtomicLong windowStart;
    private final ClockModifier clock;

    /**
     * 
     * 
     * @param maxEvents
     * @param manager
     * @param clock
     */
    public Timeline(int maxEvents, 
                    TimelineManager manager,
                    ClockModifier clock) 
    {
        this.maxEvents = maxEvents;
        this.manager = manager;
        this.countInWindow = new AtomicLong(0);
        this.windowStart = new AtomicLong(clock.getNow());
        this.clock = clock;
    }


    /**
     * Is there space for new events?
     * 
     * @param nEvents
     * @return
     */
    private boolean hasSpaceForEvents(int nEvents) {
        return countInWindow.get() + nEvents <= maxEvents;
    }

    /**
     * Shortcut for applying user-defined filter on this timeline instance.
     * 
     * @return if true, event can be added. if false, event must be rejected.
     */
    private boolean filterIn() {
        return manager.getEventFilterer().apply(this);
    }

    /**
     * Shortcut for applying user-defined filter on this timeline instance.
     *
     * @return if true, event must be rejected. if false, event can be added.
     */
    private boolean filterOut() {
        return !filterIn();
    }
    
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
    public Timeline add() {
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
    
    
    public void resetCountInWindow() {
        this.countInWindow.set(0);
    }

    // *****************
    // START: BUILT-IN FILTERS
    // *****************

    public boolean isBeforeEventThreshold(double breakpoint) {
        double p = (double) getCountInWindow() / manager.getMaxEvents();
        return p < breakpoint;
    }

    public boolean isAfterEventTreshold(double breakpoint) {
        return !isBeforeEventThreshold(breakpoint);
    }


    public boolean isBeforeWindowThreshold(double breakpoint) {
        double p = (double) (clock.getNow() - windowStart.get()) / manager.getWindow();
        return p < breakpoint;
    }

    public boolean isAfterWindowThreshold(double breakpoint) {
        return !isBeforeWindowThreshold(breakpoint);
    }

    // *****************
    // END: BUILT-IN FILTERS
    // *****************

    public long getCountInWindow() {
        return countInWindow.get();
    }

    
}
