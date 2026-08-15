package com.giuseppetavella.rate_limiter_algo.timeline;

public class SpeedAdapter {
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