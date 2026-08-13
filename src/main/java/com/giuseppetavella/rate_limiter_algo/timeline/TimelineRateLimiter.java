package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.AbstractRateLimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
 * <br><br>
 * 
 * It must be started.
 *
 */
public class TimelineRateLimiter extends AbstractRateLimiter {
    private final int nTimelines;
    private final List<Timeline> timelines;
    private final EventFilterer eventFilterer;
    private Supplier<Timeline> timelineSupplier;
    private final boolean verbose;
    private byte timelineSeq;
    private final List<ScheduledExecutorService> schedulers;
    

    public TimelineRateLimiter(Builder builder) 
    {
        // Don't care about speed - only nTimelines will be set, so speed must be adapted
        var speedValid = builder.speed != null;
        var nTimelinesValid = builder.nTimelines >= 1 && builder.nTimelines <= 24;
        
        // Cannot have both valid or invalid - must have exactly one valid
        if((speedValid && nTimelinesValid) 
                || (!speedValid && !nTimelinesValid)) 
        {
            throw new IllegalStateException("while initializing timeline rate limiter, "
                                            +"cannot have both speed and nTimelines be valid values "
                                            +"or invalid values at the same time. exactly one can be valid.");
        }
        
        // Timeline
        super(
                builder.maxEvents, 
                builder.window, 
                builder.clock
        );
        
        // number of timelines has precedence
        this.nTimelines = nTimelinesValid 
                                ? builder.nTimelines 
                                : SpeedAdapter.nTimelinesFrom(builder.speed);
        this.timelines = new ArrayList<>();
        this.verbose = builder.verbose;
        this.timelineSeq = 0;
        // If no event filterer provided, always return true.
        this.eventFilterer = builder.eventFilterer == null
                                ? (_) -> true 
                                : builder.eventFilterer;
        // If no timeline supplier was provided, use a default timeline implementation
        this.timelineSupplier = builder.timelineSupplier == null
                                        ? this::defaultTimelineSupplier
                                        : builder.timelineSupplier;
        this.schedulers = new ArrayList<>();
        
    }
    

    /**
     * Start the timelines.
     */
    public void start() {
        if(getState().equals(RateLimiterState.RUNNING)) {
            throw new IllegalStateException("cannot start rate limiter because it's already running.");
        }
        
        setState(RateLimiterState.STARTING);
        
        if(timelines == null) {
            throw new IllegalStateException("before starting timelines, "
                                    +"timelines list must be initialized. got null.");
        }
        
        if(timelineSupplier == null) {
            throw new IllegalStateException("timelineSupplier cannot be null. "
                    +"before starting the timelines, a timeline supplier must be provided.");
        }
        
        // Add timelines with given supplier
        for (int i = 0; i < nTimelines; i++) {
            timelines.add( timelineSupplier.get() );
        }

        // Schedule threads to run predefined timelines.
        // Essentially, a thread is assigned a timeline,
        // and a timeline is assigned a thread. Not enforced 
        // and should be decoupled but ok for now.
        for (int i = 0; i < nTimelines; i++) {
            Timeline t = timelines.get(i);
            
            var scheduler = Executors.newSingleThreadScheduledExecutor();
            
            scheduler.scheduleAtFixedRate(
                    buildScheduledTask(t),
                    calcInitialDelay(i),
                    window,
                    TimeUnit.MILLISECONDS
            );
            
            schedulers.add(scheduler);
        }

        setState(RateLimiterState.RUNNING);

    }


    private Timeline defaultTimelineSupplier() {
        return Timelines.newReactiveQuietFrom(this);
    }

    public void setTimelineSupplier(Supplier<Timeline> supplier) {
        this.timelineSupplier = supplier;
    }


    /**
     * Add a new event.
     * 
     * @return
     */
    @Override
    public boolean add() {
        // Add event to all timelines
        for (byte i = 0; i < timelines.size(); i++) {
            Timeline t = timelines.get(i);
            // Try adding event
            if( !t.add() ) {
                decreaseEventCountUntil(i);
                // The rejection reason specific to the timeline
                // is passed to the manager
                setRejectionReason(t.getRejectionReason());
                return false;
            }
        }       
        return true;
    }

    /**
     * Decrease event count of all timelines 
     * from 0 until {@code untilIdx} exclusive.
     * 
     * @param untilIdx
     */
    private void decreaseEventCountUntil(byte untilIdx) {
        for (byte i = 0; i < untilIdx; i++) {
            timelines.get(i).decrementEventCount();
        }
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
     * <br><br>
     * Multiply first, then divide. Before it was "(window / nTimelines) * factor",
     * but this leaked / was more imprecise, because multiplying
     * after the impliciting rounding to integer also multiplied
     * the leakage. Whereas multiplying first and then dividing
     * implies that rounding to integer happens at the last stage,
     * so accuracy is maximized.
     * 
     * <br><br> 
     * 
     * Take this example where both operations are equivalent. 
     * The result is 233.333 but only the second result is more accurate.
     * <pre>{@code 
     *         int x = 100;
     *         int y = 3;
     *         int factor = 7;
     *
     *         long res1 = (x / y) * factor;
     *         long res2 = (x * factor) / y;
     *
     *         System.out.println(res1);
     *         System.out.println(res2);
     * 
     * }
     * </pre>
     * 
     * @param factor
     * @return
     */
    public long calcBuffer(int factor) {
        return (window * factor) / nTimelines;
    }
    
    

    /**
     * Get the timeline associated to the scheduler,
     * and reset its count in window.
     *
     * @return
     */
    private Runnable buildScheduledTask(Timeline t) {
        return () -> {
            try {
                
                if(verbose) {
                    System.out.println("[timeline %d] resetting count... "
                                        +"count before reset: %d".formatted(t.getId(), t.getCountInWindow())
                    );
                }
                
                // Wakeup timeline
                t.wakeup();

            } catch (RuntimeException e) {
                System.out.println("UNCAUGHT EXCEPTION IN SCHEDULER THREAD: " + e.getMessage());
                throw new RuntimeException(e);
            }
        };
    }

    
    public byte nextTimelineSeq() {
        return ++timelineSeq;
    }

    public List<Timeline> getTimelines() {
        return timelines;
    }

    @Override
    public String toString() {
        return "TimelineRateLimiter{" +
                "maxEvents=" + maxEvents +
                ", window=" + window +
                ", nTimelines=" + nTimelines +
                '}';
    }
    

    public static class Builder {
        private int maxEvents;
        private long window;
        private Clock clock;
        private int nTimelines;
        private RateLimiterSpeed speed;
        private EventFilterer eventFilterer;
        private Supplier<Timeline> timelineSupplier;
        private boolean verbose;

        
        public Builder(int maxEvents, long window) {
            this.maxEvents = maxEvents;
            this.window = window;
        }
        
        public Builder nTimelines(int nTimelines) {
            this.nTimelines = nTimelines;
            return this;
        }
        
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder speed(RateLimiterSpeed speed) {
            this.speed = speed;
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

        public TimelineRateLimiter build() {
            return new TimelineRateLimiter(this);
        }

    }


    public static class SpeedAdapter {
        /**
         * Adapt a generic rate limiter speed to number of timelines.
         *
         * @param speed
         * @return
         */
        public static int nTimelinesFrom(RateLimiterSpeed speed) {
            var res = switch (speed) {
                case RateLimiterSpeed.SLOW -> 1;
                case RateLimiterSpeed.NORMAL -> 3;
                case RateLimiterSpeed.FAST -> 6;
                case RateLimiterSpeed.VERY_FAST -> 9;
                // Unknown input 
                default -> -1;
            };
            // Throw exception - unknown input
            if(res == -1) {
                throw new IllegalStateException("while adapting generic rate limiter speed "
                        +"to timeline rate limiter speed, could not "
                        +"interpret generic speed into specific.");
            }
            return res;
        }
    }


}
