package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.ClockImpl;
import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.RateLimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A Rate Limiter implementation prioritizing efficiency 
 * and speed over absolute accuracy.
 * The core operation, {@code add()}, has constant space 
 * and can be approximated to constant time complexity.
 * The Timeline Manager's job is to schedule and run the timelines
 * and to make the algorithm usable for the user.
 *
 */
public class TimelineManager extends RateLimiter {
    private final int nTimelines;
    private final List<Timeline> timelines;
    private final EventFilterer eventFilterer;
    private final Supplier<Timeline> timelineSupplier;
    private final boolean verbose;

    public TimelineManager(Builder builder) 
    {
        
        // Timeline
        super(
                builder.maxEvents, 
                builder.window, 
                builder.clock
        );
        
        if(builder.nTimelines < 1) {
            throw new IllegalStateException("number of timelines must be >= 1.");
        }
        
        this.timelines = new ArrayList<>();
        this.nTimelines = builder.nTimelines;
        this.verbose = builder.verbose;
        // If no event filterer provided, always return true.
        this.eventFilterer = builder.eventFilterer == null
                                ? (_) -> true 
                                : builder.eventFilterer;
        // If no timeline supplier was provided, use a default timeline implementation
        this.timelineSupplier = builder.timelineSupplier == null
                                        ? this::defaultTimelineSupplier
                                        : builder.timelineSupplier;
        
        // Build timelines with given supplier
        for (int i = 0; i < nTimelines; i++) {
            timelines.add( timelineSupplier.get() );
        }
        
        // Schedule timelines
        for (int i = 0; i < nTimelines; i++) {
            var scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(
                buildScheduledTask(i),
                calcInitialDelay(i),
                window,
                TimeUnit.MILLISECONDS
            );
        }
        
    }
    
    
    private Timeline defaultTimelineSupplier() {
        return new ReactiveTimeline(maxEvents, window, eventFilterer, clock);
    }
    
    
    /**
     * Add a new event.
     * 
     * @return
     */
    @Override
    public boolean add() {
        // Add event to all timelines
        for (var t : timelines) {
            if( !t.add() ) {
                // The rejection reason specific to the timeline
                // is passed to the manager
                setRejectionReason(t.getRejectionReason());
                return false;
            }
        }       
        return true;
    }
    
    @Override
    public boolean canAdd(int nEvents) {
        // Check if all timelines can add event
        for (var t : timelines) {
            if( !t.canAdd(nEvents) ) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canAdd() {
        return canAdd(1);
    }
    

    /**
     * Note: The count in window, for this implementation, 
     * is an average of the count in window of all timelines. 
     * @return
     */
    @Override
    public long getCountInWindow() {
        long sum = 0;
        for (var t : timelines) {
            sum += t.getCountInWindow();
        }
        return sum / timelines.size();
    }
    


    public EventFilterer getEventFilterer() {
        return eventFilterer;
    }
    

    /**
     * 
     * 
     * @param timelineIdx
     * @return
     */
    private long calcInitialDelay(int timelineIdx) {
        return calcBuffer(timelineIdx);
    }

    /**
     * Calculate the time buffer, which is simply a fraction of the window.
     * A more appropriate name could include "proportional padding".
     * For example, given a window of 100ms, 3 timelines and a factor of 2,
     * the buffer will be {@code (1000ms / 3) * 2 = 666ms}.
     * 
     * 
     * @param factor
     * @return
     */
    public long calcBuffer(int factor) {
        return (window / nTimelines) * factor;
    }
    
    

    /**
     * Get the timeline associated to the scheduler,
     * and reset its count in window.
     *
     * @param timelineIdx
     * @return
     */
    private Runnable buildScheduledTask(int timelineIdx) {
        return () -> {
            try {
                
                Timeline t = timelines.get(timelineIdx);
                
                if(verbose) {
                    System.out.println("[timeline %d] resetting count... count before reset: %d".formatted(timelineIdx, t.getCountInWindow()));
                }
                
                // Get the timeline associated to this thread
                t.wakeup();

            } catch (RuntimeException e) {
                System.out.println("UNCAUGHT EXCEPTION IN SCHEDULER THREAD: " + e.getMessage());
                throw new RuntimeException(e);
            }
        };
    }


    public static class Builder {
        private int maxEvents;
        private long window;
        private Clock clock;
        private int nTimelines;
        private EventFilterer eventFilterer;
        private Supplier<Timeline> timelineSupplier;
        private boolean verbose;

        public Builder(int maxEvents, long window, int nTimelines) {
            this.maxEvents = maxEvents;
            this.window = window;
            this.nTimelines = nTimelines;
        }
        
        public Builder(int maxEvents, long window) {
            this(maxEvents, window, 1);
        }
        
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder nTimelines(int nTimelines) {
            this.nTimelines = nTimelines;
            return this;
        }

        public Builder eventFilterer(EventFilterer eventFilterer) {
            this.eventFilterer = eventFilterer;
            return this;
        }

        public Builder timelineSupplier(Supplier<Timeline> timelineSupplier) {
            this.timelineSupplier = timelineSupplier;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public TimelineManager build() {
            return new TimelineManager(this);
        }

    }


}
