package com.giuseppetavella.rate_limiter_algo.demo;

import com.giuseppetavella.rate_limiter_algo.RateLimiter;
import com.giuseppetavella.rate_limiter_algo.timeline.EventFilterer;
import com.giuseppetavella.rate_limiter_algo.timeline.RateLimiterSpeed;
import com.giuseppetavella.rate_limiter_algo.timeline.TimelineRateLimiter;

/**
 * Examples of how you can implement a Rate Limiter 
 * in your project. Always remember to start the rate limiter.
 */
// Demo of how it would work with Spring
public class Configuration {

    /**
     * Method A: Create a rate limiter from the implementation. 
     * Pro: quick, no setup. 
     * Con: no custom type.
     * 
     * @return
     */
    public RateLimiter getAIRateLimiter() {
        int maxEvents = 20;
        long window = 1000;
        var speed = RateLimiterSpeed.NORMAL;

        EventFilterer fil = (t) -> {
            return t.isBeforeEventThreshold(.5);
            // return true;
        };
        
        var limiter = new TimelineRateLimiter.Builder(maxEvents, window)
                .speed(speed)
                .eventFilterer(fil)
                .build();

        limiter.start(); // Remember to start the limiter
        
        return limiter;
    }
    
    
    /**
     * Method B: Create a custom class, so you have a custom type. 
     * Pro: More customization. 
     * Con: More setup.
     * 
     * @return
     */
    public EmailRateLimiter getEmailRateLimiter() {
        int maxEvents = 20;
        long window = 1000;
        var speed = RateLimiterSpeed.NORMAL;
        
        EventFilterer fil = (t) -> {
            return t.isBeforeEventThreshold(.5);
            
            // return true;
        };
        
        // Limiter is already started in custom class
        return new EmailRateLimiter.Builder(maxEvents, window)
                .speed(speed)
                .eventFilterer(fil)
                .build();
    }
    
    
}
