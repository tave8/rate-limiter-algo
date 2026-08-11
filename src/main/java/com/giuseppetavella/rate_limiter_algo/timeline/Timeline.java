package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.TooManyEventsInWindowException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract base implementation of a Timeline.
 * Handles state management for event tracking, window timing, and built-in threshold calculations.
 */
public abstract class Timeline {
    protected final int maxEvents;
    protected final AtomicLong countInWindow;
    protected final TimelineManager manager;
    protected final AtomicLong windowStart;
    protected final Clock clock;

    public Timeline(int maxEvents, TimelineManager manager, Clock clock) {
        this.maxEvents = maxEvents;
        this.manager = manager;
        this.countInWindow = new AtomicLong(0);
        this.windowStart = new AtomicLong(clock.getNow());
        this.clock = clock;
    }

    /**
     * Checks whether there is enough remaining capacity for the given number of events.
     *
     * @param nEvents number of events to check
     * @return true if space is available, false otherwise
     */
    protected boolean hasSpaceForEvents(int nEvents) {
        return countInWindow.get() + nEvents <= maxEvents;
    }

    /**
     * Evaluates user-defined filter on this timeline.
     *
     * @return true if event can be added, false if it must be rejected
     */
    protected abstract boolean filterIn();

    /**
     * Evaluates user-defined filter on this timeline.
     *
     * @return true if event must be rejected, false if it can be added
     */
    protected boolean filterOut() {
        return !filterIn();
    }

    /**
     * Checks if a given number of events can be added to the timeline.
     *
     * @param nEvents number of events
     * @return true if events can be added
     */
    public abstract boolean canAdd(int nEvents);

    /**
     * Adds an event to the timeline or throws an exception if invalid.
     * Subclasses can override this to return their specific type.
     *
     * @return this timeline instance
     */
    public abstract Timeline add();

    /**
     * Refreshes the current window period and resets counter.
     */
    public abstract void refresh();

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