package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.EventsAddedTooFastException;
import com.giuseppetavella.rate_limiter_algo.TimeUtil;
import com.giuseppetavella.rate_limiter_algo.TooManyEventsInWindowException;

import java.util.concurrent.atomic.AtomicLong;

public class Timeline {
    private final int maxEvents;
    private final AtomicLong countInWindow;
    private final TimelineManager manager;
    private final AtomicLong windowStart;
    private final TimeUtil util;
    
    public Timeline(int maxEvents, TimelineManager manager) {
        this.util = new TimeUtil(); // Must go first - avoid partial initialization
        this.maxEvents = maxEvents;
        this.manager = manager;
        this.countInWindow = new AtomicLong(0);
        this.windowStart = new AtomicLong(getNow());
    }
    
    private boolean hasSpaceForEvents(int nEvents) {
        return countInWindow.get() + nEvents <= maxEvents; // No overflow 
    }
    
    private BurstProtector getBurstProtector() {
        return manager.getBurstProtector();
    }
    
    /**
     * Can add a new event?
     * 
     * @param nEvents
     * @return
     */
    public boolean canAdd(int nEvents) {
        return !hasSpaceForEvents(nEvents) && !(getBurstProtector().apply(this));
        
        // if(!hasSpaceForEvents(nEvents)) {
        //     return false;
        // }
        //
        //
        //
        // if(isBurstProtectionEnabled()) {
        //     if(hasReachedWindowThreshold()) {
        //         return !hasReachedEventThreshold(); // Can add only if not reached event threshold
        //     }
        // }
        //
        // return true;
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
        
        if( !(getBurstProtector().apply(this)) ) {
            throw new EventsAddedTooFastException(
                    maxEvents, 
                    countInWindow.get(), 
                    "Now: %d, Window start: %d, Diff: %d".formatted(getNow(), windowStart.get(), getNow()-windowStart.get())
            );
        }
        
        this.countInWindow.getAndIncrement();
        return this;
    }

    
    /**
     * Has the count of events in this timeline, in the current period, reached 
     * a percentage threshold, compared to max events allowed?
     * 
     * For example, when this method is called, have 95% of max events 
     * already been added in the current period?
     * 
     * This is useful for implementing preventive back-off, that is, 
     * instead of waiting to reach exactly 100% of max events, 
     * disallow new events from being added right now.
     * 
     * "Preventing is better than curing" type of thing.
     * 
     * When combined with <code>isInLastBuffer()</code>, we can 
     * create custom logic such as "If a new event is trying to be added 
     * while we are in the last buffer AND 95% of events has already been reached (in this period), 
     * then disallow new events from being added right now."
     *
     * @return
     */
    // public boolean hasReachedPercThreshold() {
    //     // The percentage of events added compared to the max events
    //     double currPerc = (double) countInWindow.get() / (double) maxEvents;
    //     // The percentage of current events is greater than a set percentage threshold
    //     return currPerc >= percThreshold;
    // }


    public boolean isBeforeEventThreshold(double breakpoint) {
        double p = (double) getCountInWindow() / manager.getMaxEvents();
        return p < breakpoint;
    }

    public boolean isAfterEventTreshold(double breakpoint) {
        return !isBeforeEventThreshold(breakpoint);
    }


    public boolean isBeforeWindowThreshold(double breakpoint) {
        double p = (double) (getNow() - windowStart.get()) / manager.getWindow();
        return p < breakpoint;
    }

    public boolean isAfterWindowThreshold(double breakpoint) {
        return !isBeforeWindowThreshold(breakpoint);
    }
    
    
    /**
     * Refresh the current period.
     */
    public void refresh() {
        resetCountInWindow();
        this.windowStart.set(getNow());
    }
    
    
    public void resetCountInWindow() {
        this.countInWindow.set(0);
    }

    public long getCountInWindow() {
        return countInWindow.get();
    }

    public long getNow() {
        return util.getNow();
    }

    /**
     * Is this the last time buffer in the window?
     *
     * Here's an example with 3 Timelines. The asterisks represent the 
     * time in the last buffer in each window in each timeline.
     *
     * <pre>
     *     ------------------------------------------------------> time
     *
     *                *****          *****          *****
     *     |--------------|--------------|--------------|
     *                     *****          *****          *****
     *          |--------------|--------------|--------------|
     *                          *****          *****          *****
     *               |--------------|--------------|--------------|
     * </pre>
     *
     * This can be used to create custom rate limiting logic such as: 
     * "If a new event is trying to be added in the last buffer period, 
     * and 95% of events have already been added from the start of this period
     * (in percentage to the max events allowed in the window), then disallow insertion
     * of new events before you even get to the exact max events (we assume you'll get there, 
     * so preventive back-off)".
     *
     * @return
     */
    // public boolean isThisLastBuffer() {
    //     var startLastBuffer = windowStart + manager.calcLastBuffer();
    //     return getNow() >= startLastBuffer;
    // }


    /**
     * Shortcut. 
     *
     * @return
     */
    // private boolean hasReachedEventThreshold() {
    //     return manager.getBurstProtection().hasReachedEventThreshold(countInWindow.get());
    // }
    //
    //
    // /**
    //  * Shortcut.
    //  *
    //  * @return
    //  */
    // private boolean hasReachedWindowThreshold() {
    //     return manager.getBurstProtection().hasReachedWindowThreshold(getNow(), windowStart.get());
    // }
    //
    // /**
    //  * Shortcut.  
    //  *
    //  * @return
    //  */
    // private boolean isBurstProtectionEnabled() {
    //     return manager.getBurstProtection().isEnabled();
    // }

}
