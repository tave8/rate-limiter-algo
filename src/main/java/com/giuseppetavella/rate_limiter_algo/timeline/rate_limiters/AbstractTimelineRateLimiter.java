package com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters;

import com.giuseppetavella.rate_limiter_algo.AbstractRateLimiter;
import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.timeline.EventFilterer;
import com.giuseppetavella.rate_limiter_algo.timeline.RateLimiterSpeed;
import com.giuseppetavella.rate_limiter_algo.timeline.timelines.AbstractTimeline;
import com.giuseppetavella.rate_limiter_algo.timeline.timelines.Timelines;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public abstract class AbstractTimelineRateLimiter extends AbstractRateLimiter {
    protected final int nTimelines;
    protected final List<AbstractTimeline> timelines;
    protected byte timelineSeq;
    protected final EventFilterer eventFilterer;
    protected Supplier<AbstractTimeline> timelineSupplier;
    protected final boolean verbose;
     
    
    public AbstractTimelineRateLimiter(int maxEvents, 
                                       long window, 
                                       Clock clock,
                                       int nTimelines,
                                       EventFilterer fil,
                                       boolean verbose,
                                       Supplier<AbstractTimeline> timelineSupplier) 
                                        throws IllegalArgumentException 
    {
        // Rate limiter
        super(
                maxEvents, 
                window, 
                clock
        );
        
        this.timelines = new ArrayList<>();
        this.timelineSeq = 0;
        this.nTimelines = nTimelines;
        // If no event filterer provided, always return true.
        this.eventFilterer = fil;
        this.verbose = verbose;
        // If no timeline supplier was provided, use a default timeline implementation
        this.timelineSupplier  = timelineSupplier == null
                ? this::defaultTimelineSupplier
                : timelineSupplier;
    }

    
    protected AbstractTimeline defaultTimelineSupplier() {
        return Timelines.newEfficient(this);
    }

    public void setTimelineSupplier(Supplier<AbstractTimeline> supplier) {
        this.timelineSupplier = supplier;
    }

    public Supplier<AbstractTimeline> getTimelineSupplier() {
        return timelineSupplier;
    }

    public EventFilterer getEventFilterer() {
        return eventFilterer;
    }

    public abstract void start();
    
    public abstract void stop();

    public boolean isVerbose() {
        return verbose;
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

    

    public List<AbstractTimeline> getTimelines() {
        return timelines;
    }

    public byte nextTimelineSeq() {
        return ++timelineSeq;
    }

    public int getnTimelines() {
        return nTimelines;
    }

    @Override
    public String toString() {
        return "AbstractTimelineRateLimiter{" +
                "maxEvents=" + maxEvents +
                ", window=" + window +
                ", nTimelines=" + nTimelines +
                '}';
    }
    
}
