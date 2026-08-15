package com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters;

import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.timeline.*;
import com.giuseppetavella.rate_limiter_algo.timeline.timelines.AbstractTimeline;

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
public class TimelineNThreadsRateLimiter extends AbstractTimelineRateLimiter {
    private final List<ScheduledExecutorService> schedulers;

    public TimelineNThreadsRateLimiter(Builder builder) 
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

        // Timeline rate limiter
        super(
                builder.maxEvents,
                builder.window,
                builder.clock,
                // number of timelines
                nTimelinesValid
                        ? builder.nTimelines
                        : SpeedAdapter.nTimelinesFrom(builder.speed),
                // If no event filterer provided, always return true.
                builder.eventFilterer == null
                        ? (_) -> true
                        : builder.eventFilterer, 
                builder.verbose,
                builder.timelineSupplier
        );

        this.schedulers = new ArrayList<>();

    }


    /**
     * Start the timelines.
     */
    @Override
    public void start() {
        synchronized (this) {
            if(getState().equals(RateLimiterState.RUNNING)) {
                throw new IllegalStateException("cannot start rate limiter because it's already running.");
            }

            if(getState().equals(RateLimiterState.STOPPED)) {
                throw new IllegalStateException("cannot start rate limiter because it has already been "
                        +"stopped. a rate limiter instance cannot be restarted "
                        +"once stopped - create a new instance instead.");
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
            for (int i = 0; i < nTimelines; i++) {
                AbstractTimeline t = timelines.get(i);

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
    }

    @Override
    public void stop() {
        synchronized (this) {
            if(getState().equals(RateLimiterState.STOPPED)) {
                throw new IllegalStateException("rate limiter is already stopped, cannot stop it again.");
            }

            if(getState().equals(RateLimiterState.NEW)) {
                throw new IllegalStateException("rate limiter was never started, so cannot stop it.");
            }

            setState(RateLimiterState.STOPPING);

            schedulers.forEach(scheduler -> {
                scheduler.shutdown();
            });

            setState(RateLimiterState.STOPPED);
        }
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
            AbstractTimeline t = timelines.get(i);
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



    /**
     * 
     * 
     * @param timelineIdx
     * @return
     */
    private long calcInitialDelay(int timelineIdx) {
        // BUGFIX: it was `calcBuffer(timelineIdx)` 
        // but timeline 0 would start with initial delay of 0,
        // which probably means that it was run immediately.
        return window - calcBuffer(timelineIdx);
    }


    /**
     * Get the timeline associated to the scheduler,
     * and reset its count in window.
     *
     * @return
     */
    private Runnable buildScheduledTask(AbstractTimeline t) {
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



    public static class Builder {
        private int maxEvents;
        private long window;
        private Clock clock;
        private int nTimelines;
        private RateLimiterSpeed speed;
        private EventFilterer eventFilterer;
        private Supplier<AbstractTimeline> timelineSupplier;
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

        public Builder timelineSupplier(Supplier<AbstractTimeline> timelineSupplier) {
            this.timelineSupplier = timelineSupplier;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public TimelineNThreadsRateLimiter build() {
            return new TimelineNThreadsRateLimiter(this);
        }

    }





}
