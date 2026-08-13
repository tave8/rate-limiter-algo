package com.giuseppetavella.rate_limiter_algo.timeline;

public class Timelines {
    
    public static ReactiveTimeline newReactiveFrom(TimelineRateLimiter m) {
        return ReactiveTimeline.Builder.newFromManager(m);
    }

    public static ReactiveQuietTimeline newReactiveQuietFrom(TimelineRateLimiter m) {
        return ReactiveQuietTimeline.Builder.newFromManager(m);
    }

    public static ReactiveQuietBackoffTimeline newReactiveQuietBackoffFrom(TimelineRateLimiter m) {
        return ReactiveQuietBackoffTimeline.Builder.newFromManager(m);
    }
    
}
