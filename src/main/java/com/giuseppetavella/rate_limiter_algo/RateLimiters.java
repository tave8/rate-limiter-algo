package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.timeline.RateLimiterSpeed;
import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.TimelineRateLimiter;

public class RateLimiters {
    
    public static RateLimiter newDefault(int maxEvents, long window) {
        var l = new TimelineRateLimiter.Builder(maxEvents, window)
                .speed(RateLimiterSpeed.NORMAL)
                .build();
                        
        l.start();
        
        return l;
    }
    
    
}
