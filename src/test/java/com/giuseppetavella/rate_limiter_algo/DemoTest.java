package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.core.RateLimiter;
import com.giuseppetavella.rate_limiter_algo.examples.Config;
import com.giuseppetavella.rate_limiter_algo.examples.EmailRateLimiter;
import com.giuseppetavella.rate_limiter_algo.core.timeline.TimelineRateLimiter;
import com.giuseppetavella.rate_limiter_algo.core.timeline.Timelines;
import com.giuseppetavella.rate_limiter_algo.examples.EmailService;
import org.junit.jupiter.api.Test;

public class DemoTest {
    @Test
    void test1() throws InterruptedException {
        int maxEvents = 100;
        long window = 1000;
        
        
        TimelineRateLimiter manager = new TimelineRateLimiter.Builder(maxEvents, window).nTimelines(1)
                .build();

        manager.setTimelineSupplier(() -> Timelines.newReactiveQuietBackoffFrom(manager));

        manager.start();
        
        // RateLimiter rateLimiter2 = new HistoryQueue(maxEvents, window, clock2);
        
        // rateLimiter1.after(100);

        // for (int i = 0; i < 1000; i++) {
        //     // testClock.after(120);
        //     // System.out.println(testClock.measureElapsed());
        //     // testClock.after(100).after(30);
        //     // clock1.after(150);
        //     // rateLimiter1.add();
        //     // System.out.println(rateLimiter1.getCountInWindow());
        //     // Thread.sleep(150);
        //     // rateLimiter1.add().after(100);
        //    
        // }
        
    }
    
    @Test
    void usage1() throws InterruptedException {
        var config = new Config();
        // Rate limiters. One is a custom type, the other is a library type. 
        EmailRateLimiter emailLimiter = config.getEmailRateLimiter(); // Custom type
        RateLimiter aiLimiter = config.getAIRateLimiter(); // Library type
        

        for (int i = 0; i < 500; i++) {
            if(emailLimiter.add()) {
                System.out.println("added: " + i);
            } else {
                System.out.println("could not add "+i+" because: " + emailLimiter.getRejectionReason());                
            }
            Thread.sleep(10);
        }
    }

    @Test
    void usage2() throws InterruptedException {
        var config = new Config();
        EmailRateLimiter emailLimiter = config.getEmailRateLimiter(); 
        var emailService = new EmailService(emailLimiter); // Pass the rate limiter to the service 

        for (int i = 0; i < 500; i++) {
            if(emailLimiter.add()) {
                System.out.println("added: " + i);
            } else {
                System.out.println("could not add "+i+" because: " + emailLimiter.getRejectionReason());
            }
            Thread.sleep(10);
        }
    }
}
