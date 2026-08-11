package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.ClockModifier;
import com.giuseppetavella.rate_limiter_algo.RateLimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A Rate Limiter implementation prioritizing efficiency 
 * and speed over absolute accuracy.
 * The core operation, {@code add()}, has constant space 
 * and can be approximated to constant time complexity.
 * The Timeline Manager's job is to schedule and run the timelines
 * and to make the algorithm usable for the user.
 *
 */
public class TimelineManager implements RateLimiter {
    private final int maxEvents;
    private final long window;
    private final int nTimelines;
    private final List<Timeline> timelines;
    private final EventFilterer eventFilterer;
    private boolean verbose;
    private final ClockModifier clock;
    
    /**
     * Most custom.
     * 
     * @param maxEvents
     * @param window
     * @param nTimelines
     * @param fil
     */
    public TimelineManager(int maxEvents,
                           long window,
                           int nTimelines,
                           EventFilterer fil,
                           ClockModifier clock) 
                               throws IllegalArgumentException
    {
        if(window < 100) {
            throw new IllegalArgumentException("Time window must be >= 100.");
        }
        if(maxEvents < 0) {
            throw new IllegalArgumentException("Max events must be >= 0.");
        }
        this.maxEvents = maxEvents;
        this.window = window;
        this.nTimelines = nTimelines;
        this.clock = clock;
        this.eventFilterer = fil;
        this.timelines = new ArrayList<>();
        this.verbose = false;
        init();
    }

    public TimelineManager(int maxEvents, 
                           long window, 
                           int nTimelines, 
                           EventFilterer eventFilterer) 
    {
        this(maxEvents, window, nTimelines, eventFilterer, new Clock());
    }

    /**
     * No filter.
     * 
     * @param maxEvents
     * @param window
     * @param nTimelines
     */
    public TimelineManager(int maxEvents,
                           long window,
                           int nTimelines) 
    {
        this(maxEvents, window, nTimelines, (_) -> true);
    }

    /**
     * 1 timeline, no filter.
     * 
     * @param maxEvents
     * @param window
     */
    public TimelineManager(int maxEvents, long window) 
    {
        this(maxEvents, window, 1);   
    }

    
    
    /**
     * Add a new event.
     * 
     * @return
     */
    @Override
    public TimelineManager add() {
        // Add event to all timelines
        for (var timeline : timelines) {
            timeline.add();
        }       
        return this;
    }
    
    @Override
    public boolean canAdd(int nEvents) {
        // Check if all timelines can add event
        for (var timeline : timelines) {
            if(!timeline.canAdd(nEvents)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canAdd() {
        return canAdd(1);
    }

    @Override
    public int getMaxEvents() {
        return maxEvents;
    }

    @Override
    public long getWindow() {
        return window;
    }

    @Override
    public RateLimiter after(long delay) {
        clock.after(delay);
        return this;
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
     * Set verbosity. 
     * 
     * @param verbose
     * @return
     */
    public TimelineManager verbose(boolean verbose) {
        this.verbose = verbose;   
        return this;
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
                
                var timeline = timelines.get(timelineIdx);
                
                if(verbose) {
                    System.out.println("[timeline %d] resetting count... count before reset: %d".formatted(timelineIdx, timeline.getCountInWindow()));
                }
                
                // Get the timeline associated to this thread
                timeline.refresh();

            } catch (RuntimeException e) {
                System.out.println("UNCAUGHT EXCEPTION IN SCHEDULER THREAD: " + e.getMessage());
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * Initialize instance. Must not be executed twice.
     */
    private void init() {
        // Build timelines
        for (int i = 0; i < nTimelines; i++) {
            timelines.add(new Timeline(maxEvents, this, clock));
        }

        // Schedule as many threads as timelines
        // TODO: schedule always one thread, but run the tasks 
        // as if they still had the same effect.

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

    
}
