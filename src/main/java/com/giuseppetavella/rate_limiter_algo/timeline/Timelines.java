package com.giuseppetavella.rate_limiter_algo.timeline;

public class Timelines {
    
    public static ReactiveTimeline newReactiveFrom(TimelineManager m) {
        return ReactiveTimeline.Builder.newFromManager(m);
    }

    public static ReactiveQuietTimeline newReactiveQuietFrom(TimelineManager m) {
        return ReactiveQuietTimeline.Builder.newFromManager(m);
    }

    public static ReactiveQuietBackoffTimeline newReactiveQuietBackoffFrom(TimelineManager m) {
        return ReactiveQuietBackoffTimeline.Builder.newFromManager(m);
    }
    
}
