package com.giuseppetavella.rate_limiter_algo.timeline.timelines;

import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.RejectionReason;
import com.giuseppetavella.rate_limiter_algo.timeline.EventFilterer;
import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.AbstractTimelineRateLimiter;

/**
 * A Reactive Quiet Timeline provides maximum accuracy
 * as well as communicating errors with codes instead of throwing exceptions.
 * 
 * 
 */
public class Timeline extends AbstractTimeline {

    public Timeline(Builder builder) 
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
        
        // Atomic update 
        long curr;
        do {
            curr = countInWindow.get();
            
            if(wouldOverflow()) {
                setRejectionReason(RejectionReason.WOULD_OVERFLOW);
                return false;
            }
            
            if(filterOut()) {
                setRejectionReason(RejectionReason.FILTERED_OUT);
                return false;
            }
            
        } while(!countInWindow.compareAndSet(curr, curr+1));
        
        return true;
    }

    /**
     * Refreshes the current window period and resets counter.
     */
    @Override
    public void wakeup() {
        this.countInWindow.set(0);
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

        public static Timeline newFromManager(AbstractTimelineRateLimiter manager) {
            var builder = new Timeline.Builder(
                    manager.getMaxEvents(),
                    manager.getWindow(),
                    manager.nextTimelineSeq()
            );
            builder.clock(manager.getClock());
            builder.eventFilterer(manager.getEventFilterer());
            return new Timeline(builder);
        }
        
        
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder eventFilterer(EventFilterer eventFilterer) {
            this.eventFilterer = eventFilterer;
            return this;
        }

        public Timeline build() {
            return new Timeline(this);
        }
    }

}
