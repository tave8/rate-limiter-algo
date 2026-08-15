package com.giuseppetavella.rate_limiter_algo.timeline.timelines;

import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.AbstractTimelineRateLimiter;

public class Timelines {
    
    public static Timeline newEfficient(AbstractTimelineRateLimiter m) {
        return Timeline.Builder.newFromManager(m);
    }
    
}
