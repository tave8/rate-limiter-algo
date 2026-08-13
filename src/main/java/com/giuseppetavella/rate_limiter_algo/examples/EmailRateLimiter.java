package com.giuseppetavella.rate_limiter_algo.examples;

import com.giuseppetavella.rate_limiter_algo.core.RateLimiter;
import com.giuseppetavella.rate_limiter_algo.core.RejectionReason;
import com.giuseppetavella.rate_limiter_algo.core.timeline.EventFilterer;
import com.giuseppetavella.rate_limiter_algo.core.timeline.RateLimiterSpeed;
import com.giuseppetavella.rate_limiter_algo.core.timeline.TimelineRateLimiter;

/**
 * Example usage. Your service (an Email API)
 * <i>is</i> a Rate Limiter and <i>has</i> a Rate Limiter implementation.
 * Now that you have your custom class, you can instantiate it and 
 * use it as a singleton to rate limit your Email API service. For example
 * in Spring you can create a bean with return type {@code EmailAPIRateLimiter}
 * and configure in the bean the {@code maxEvents} 
 */
public class EmailRateLimiter implements RateLimiter {
    
    private final TimelineRateLimiter limiter; // Rate Limiter implementation
    
    public EmailRateLimiter(Builder builder) {
        this.limiter = new TimelineRateLimiter.Builder(builder.maxEvents, builder.window)
                .speed(builder.speed)
                .eventFilterer(builder.eventFilterer)
                .build();

        limiter.start(); // Remember to start the rate limiter
    }
    
    @Override
    public boolean add() {
        return limiter.add();
    }

    @Override
    public boolean canAdd() {
        return limiter.canAdd();
    }

    @Override
    public RejectionReason getRejectionReason() {
        return limiter.getRejectionReason();
    }
    
    
    public static class Builder {
        private int maxEvents;
        private long window;
        private RateLimiterSpeed speed;
        private EventFilterer eventFilterer;
        
        public Builder(int maxEvents, long window) {
            this.maxEvents = maxEvents;
            this.window = window;
        }
        
        public Builder speed(RateLimiterSpeed speed) {
            this.speed = speed;
            return this;
        }
        
        public Builder eventFilterer(EventFilterer fil) {
            this.eventFilterer = fil;
            return this;
        }

        public EmailRateLimiter build() {
            return new EmailRateLimiter(this);
        }
        
    }
    
}
