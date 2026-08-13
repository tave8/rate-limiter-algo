package com.giuseppetavella.rate_limiter_algo.examples;

import com.giuseppetavella.rate_limiter_algo.core.RateLimiter;
import com.giuseppetavella.rate_limiter_algo.core.timeline.EventFilterer;
import com.giuseppetavella.rate_limiter_algo.core.timeline.RateLimiterSpeed;
import com.giuseppetavella.rate_limiter_algo.core.timeline.TimelineRateLimiter;

/**
 * Examples of how you can implement a Rate Limiter 
 * in your project. Always remember to start the rate limiter.
 * 
 * <br><br>
 * 
 * Imagine this is a configuration class in Spring. 
 * You annotate the method to be a bean, which means 
 * you get a singleton of that bean. Because you associate
 * a rate limiter with a service, and a service with a rate limiter
 * (1 rate limiter instance : 1 service instance) then each bean corresponds
 * to a rate limiter instance. 
 * 
 */
public class Config {

    /**
     * Method A: Create a rate limiter from a default implementation. 
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
        
        // Creating a rate limiter directly from the library
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

        // Creating a rate limiter from your own implementation
        return new EmailRateLimiter.Builder(maxEvents, window)
                .speed(speed)
                .eventFilterer(fil)
                .build();
    }
    
    
}
