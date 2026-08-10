package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.RateLimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A Rate Limiter implementation prioritizing efficiency and speed over absolute accuracy.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Memory usage does not scale linearly with request volume.</li>
 *   <li>Does not require scanning all historical events in the current window.</li>
 * </ul>
 *
 * <p><strong>Complexity Analysis:</strong>
 * <ul>
 *   <li><strong>Space:</strong> {@code O(1)} space complexity on all operations.</li>
 *   <li><strong>Time:</strong> for {@code add()} and {@code canAdd()}; {@code O(K)} where {@code K} is the 
 *       number of timelines, as each request increments counters across all timelines.</li>
 * </ul>
 */
public class TimelineManager implements RateLimiter {
    private final int maxEvents;
    private final long window;
    private final int nTimelines;
    private final List<Timeline> timelines;
    private final EventFilterer eventFilterer;
    private boolean verbose;
    
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
                           EventFilterer fil) 
                               throws IllegalArgumentException
    {
        if(window <= 0) {
            throw new IllegalArgumentException("Time window must be > 0.");
        }
        if(maxEvents < 0) {
            throw new IllegalArgumentException("Max events must be >= 0.");
        }
        this.maxEvents = maxEvents;
        this.window = window;
        this.nTimelines = nTimelines;
        this.timelines = new ArrayList<>();
        this.eventFilterer = fil;
        this.verbose = false;
        init();
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


    public EventFilterer getEventFilterer() {
        return eventFilterer;
    }
    
    // public boolean canAdd() {
    //     return canAdd(1);
    // }


    /**
     * Core idea of the Timeline implementation.
     * By using the initial delay of the timeline as a permanent shift in the sense of time,
     * each timeline effectively has its own start window.
     *
     * The initial delay of the timeline (and thus, of the scheduler) 
     * is <code>(window / nTimelines) * i</code> and the reasoning behind it is as follows.
     *
     * Let Timeline 0 be the first timeline. Then Timeline 0 will start
     * with an initial delay of 0. Let Timeline 1 be the second timeline.
     * Then Timeline 1 will start within the window, but in after a fraction of time 
     * has passed. This fraction of time is evenly distributed, so to speak.
     * Concretely, this fraction of time is simply <code>window / nTimelines</code>
     * so that the initial delay of each timeline is an exact fraction of the window.
     * However, to make assigning the initial delay each timeline an automatic process,
     * we need to schedule each timeline to start after the initial delay of the previous timeline.
     * Which is the formula becomes <code>(window / nTimelines) * i</code>, where i is the i-th timeline.
     *
     * <br><br>
     *
     * Many timelines starting at different delays effectively increases precision.
     * With this implementation, it's almost impossible to get an exact guarantee 
     * that the given max number of events is respected. Instead, precision 
     * is loosened up to allow for speed.
     *
     * Precision can be increased by increasing the number of timelines.
     * However, because of the nature of threads, there's no exact guarantee 
     * on timing. Because of the overall pragmatic nature of this implementation, 
     * and because it gives up accuracy to gain in efficiency, optimal results
     * should be assessed empirically. For example, by increasing the number of timelines,
     * it's possible there are no increases in accuracy.
     *
     *
     *
     * <pre>
     *    1 timeline: 
     *
     *      |--------------|--------------|--------------|--------------
     *
     *
     *    2 timelines:
     *
     *      |--------------|--------------|--------------|--------------
     *              |--------------|--------------|--------------|--------------
     *
     *
     *     3 timelines:
     *
     *      |--------------|--------------|--------------|--------------
     *            |--------------|--------------|--------------|--------------
     *                 |--------------|--------------|--------------|--------------    
     *
     *     4 timelines:
     *
     *      |--------------|--------------|--------------|--------------
     *          |--------------|--------------|--------------|--------------
     *              |--------------|--------------|--------------|--------------   
     *                  |--------------|--------------|--------------|--------------
     *
     *
     *     5 timelines:
     *
     *      |--------------|--------------|--------------|--------------
     *         |--------------|--------------|--------------|--------------
     *            |--------------|--------------|--------------|--------------   
     *               |--------------|--------------|--------------|--------------
     *                  |--------------|--------------|--------------|--------------    
     *
     * </pre>
     *
     * @param timelineIdx
     * @return
     */
    private long calcInitialDelay(int timelineIdx) {
        return calcBuffer(timelineIdx);
    }

    /**
     * Calculate the time buffer, which is simply the number of milliseconds 
     * representing some amount of time that is proportional to the window.
     *
     * <br>
     * It's used in a formula like <code>windowStart + lastBuffer</code>
     * to effectively locate the start of the last buffer in the current period.
     *
     * <br><br>
     * Some useful cases:
     * <ul>
     *     <li>A factor of 0 returns 0.</li>
     *     <li>A factor of <code>nTimelines-1</code> is used to locate 
     *          the start of the last buffer in the current period.  
     *     <li>A factor of <code>nTimelines</code> is equivalent to end of the window.</li>
     * </ul>
     *
     *
     * @return
     */
    public long calcBuffer(int factor) {
        return (window / nTimelines) * factor;
    }


    /**
     * Calculate the last time buffer of the window.
     * This is useful for knowing whether we are "towards the end"
     * of a window.
     *
     * <pre>
     *     ------------------------------------------------------> time
     *
     *                 |--- buffer start
     *    window start |           
     *     |           v
     *     v           *****          *****          *****
     *     |--------------|--------------|--------------|     timeline
     *
     *     |----------|
     *      last buffer 
     *
     * </pre>
     *
     * @return a number, in milliseconds, that indicates the buffer (almost like a left padding)
     *          that can be added to the window start, to get the buffer start
     */
    // public long calcLastBuffer() {
    //     return calcBuffer(nTimelines-1);
    // }

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
            timelines.add(new Timeline(maxEvents, this));
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
