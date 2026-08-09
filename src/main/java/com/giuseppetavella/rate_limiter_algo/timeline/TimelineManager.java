package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.TooManyEventsInWindowException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
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
public class TimelineManager {
    private final int maxEvents;
    private final long window;
    private final int nTimelines;
    private final List<Timeline> timelines;
    
    public TimelineManager(int maxEvents, long window, int nTimelines) {
        this.maxEvents = maxEvents;
        this.window = window;
        this.nTimelines = nTimelines;
        this.timelines = new ArrayList<>();

        // Build timelines
        for (int i = 0; i < nTimelines; i++) {
            timelines.add(new Timeline(maxEvents));
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
        return (window / nTimelines) * timelineIdx;  
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
            // try {
                var timeline = timelines.get(timelineIdx);
                System.out.println("[timeline %d] resetting count in window... count before reset: %d".formatted(timelineIdx, timeline.getCountInWindow()));
                // Get the timeline associated to this thread
                timeline.resetCountInWindow();
                
            // } catch (TooManyEventsInWindowException e) {
            //     // System.out.println(e.getMessage());
            //     // throw new RuntimeException(e);
            // }
        };
    }
    
    
    public TimelineManager add() {
        // Add event to all timelines
        for (var timeline : timelines) {
            timeline.add();
        }       
        return this;
    }
    
    // public boolean canAdd(int nEvents) {
    //     // Check if all timelines can add event
    //     for (var timeline : timelines) {
    //         if(!timeline.canAdd(nEvents)) {
    //             return false;
    //         }
    //     }
    //     return true;
    // }
    
    // public boolean canAdd() {
    //     return canAdd(1);
    // }
    
}
