package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.EventsAddedTooFastException;
import com.giuseppetavella.rate_limiter_algo.TimeUtil;
import com.giuseppetavella.rate_limiter_algo.TooManyEventsInWindowException;

import java.util.concurrent.atomic.AtomicLong;

public class Timeline {
    private final int maxEvents;
    private final AtomicLong countInWindow;
    private final TimelineManager manager;
    private volatile long windowStart;
    private final TimeUtil util;
    private final double percThreshold;
    
    public Timeline(int maxEvents, TimelineManager manager) {
        this.util = new TimeUtil(); // Must go first - avoid partial initialization
        this.maxEvents = maxEvents;
        this.manager = manager;
        this.countInWindow = new AtomicLong(0);
        this.windowStart = getNow();
        this.percThreshold = 0.95;
    }

    /**
     * Can add a new event?
     * 
     * @param nEvents
     * @return
     */
    public boolean canAdd(int nEvents) {
        return countInWindow.get() + nEvents <= maxEvents; // No overflow
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
    public boolean isThisLastBuffer() {
        var startLastBuffer = windowStart + manager.calcLastBuffer();
        return getNow() >= startLastBuffer;
    }
    
    public boolean canAdd() {
        return canAdd(1);
    }

    /**
     * Add a new event.
     * 
     * @return
     */
    public Timeline add() {
        if(!canAdd()) {
            throw new TooManyEventsInWindowException(maxEvents);
        }
        
        if(isThisLastBuffer()) {
            if(hasReachedPercThreshold()) {
                // System.out.println("curr events / max events: %f perc".formatted(currPerc));
                throw new EventsAddedTooFastException(maxEvents, countInWindow.get());
            }
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
    public boolean hasReachedPercThreshold() {
        // The percentage of events added compared to the max events
        double currPerc = (double) countInWindow.get() / (double) maxEvents;
        // System.out.println("curr perc reached: " + currPerc);
        // The percentage of current events is greater than a set percentage threshold
        return currPerc >= percThreshold;
    }

    /**
     * Refresh the current period.
     */
    public void refresh() {
        resetCountInWindow();
        // var prev = windowStart;
        this.windowStart = getNow();
        // System.out.println("delta start period: " + (windowStart - prev));
    }
    
    
    public void resetCountInWindow() {
        // var currCount = countInWindow.get(); 
        // var overflow = currCount > maxEvents; 
        this.countInWindow.set(0);
        // if(overflow) {
        //     throw new TooManyEventsInWindowException(maxEvents, currCount);
        // }
    }

    public long getCountInWindow() {
        return countInWindow.get();
    }

    public long getNow() {
        return util.getNow();
    }
}
