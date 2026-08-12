package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.RejectionReason;
import com.giuseppetavella.rate_limiter_algo.TooManyEventsInWindowException;

/**
 * A Reactive Timeline provides maximum accuracy.
 * 
 * 
 */
public class ReactiveTimeline extends Timeline {

    public ReactiveTimeline(Builder builder) 
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
            throw new TooManyEventsInWindowException(maxEvents);
        }
        
        if(filterOut()) {
            setRejectionReason(RejectionReason.FILTERED_OUT);
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
        
        public static ReactiveTimeline newFromManager(TimelineManager manager) {
            var builder = new Builder(
                    manager.getMaxEvents(), 
                    manager.getWindow(),
                    manager.nextTimelineSeq()
            );
            builder.clock(manager.getClock());
            builder.eventFilterer(manager.getEventFilterer());
            return new ReactiveTimeline(builder);
        }
        
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }
        
        public Builder eventFilterer(EventFilterer eventFilterer) {
            this.eventFilterer = eventFilterer;
            return this;
        }
        
        public ReactiveTimeline build() {
            return new ReactiveTimeline(this);
        }
    }
    
}
