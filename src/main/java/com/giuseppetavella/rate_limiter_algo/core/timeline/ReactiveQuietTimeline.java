package com.giuseppetavella.rate_limiter_algo.core.timeline;

import com.giuseppetavella.rate_limiter_algo.core.Clock;
import com.giuseppetavella.rate_limiter_algo.core.RejectionReason;

/**
 * A Reactive Quiet Timeline provides maximum accuracy
 * as well as communicating errors with codes instead of throwing exceptions.
 * 
 * 
 */
public class ReactiveQuietTimeline extends Timeline {

    public ReactiveQuietTimeline(Builder builder) 
    {
        super(
                builder.maxEvents,
                builder.window,
                builder.clock,
                builder.eventFilterer,
                builder.id
        );
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
        return eventFilterer.filter(this);
    }



    public static class Builder {
        private int maxEvents;
        private long window;
        private Clock clock;
        private EventFilterer eventFilterer;
        private byte id;

        public Builder(int maxEvents, long window, byte id) {
            this.maxEvents = maxEvents;
            this.window = window;
            this.id = id;
        }

        public static ReactiveQuietTimeline newFromManager(TimelineRateLimiter manager) {
            var builder = new ReactiveQuietTimeline.Builder(
                    manager.getMaxEvents(),
                    manager.getWindow(),
                    manager.nextTimelineSeq()
            );
            builder.clock(manager.getClock());
            builder.eventFilterer(manager.getEventFilterer());
            return new ReactiveQuietTimeline(builder);
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder eventFilterer(EventFilterer eventFilterer) {
            this.eventFilterer = eventFilterer;
            return this;
        }

        public ReactiveQuietTimeline build() {
            return new ReactiveQuietTimeline(this);
        }
    }

}
