package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.timeline.TimelineRateLimiter;
import com.giuseppetavella.rate_limiter_algo.timeline.Timelines;
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
    void test2() throws InterruptedException {
        int capacity = 1_000;
        long windowMs = 100;

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs).nTimelines(1).build();
        limiter.start();
        // Thread.sleep(1000);

        int acceptedFirstBatch = 0;
        for (int i = 0; i < capacity * 2; i++) {
            if (limiter.add()) acceptedFirstBatch++;
        }

        System.out.println(acceptedFirstBatch);
    }

    // @Test
    // void test3() throws InterruptedException {
    //
    //     // Define params
    //     int maxEvents = 1_000;
    //     long windowMs = 1_000;
    //
    //     // Instantiate 
    //     TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(maxEvents, windowMs)
    //             .build();
    //
    //     // Start
    //     limiter.start();
    //
    //     // Apply rate limit (add event)
    //     if( !limiter.add() ) {
    //         throw new RuntimeException("event rejected because " + limiter.getRejectionReason());
    //     }
    //
    //
    //     // Thread.sleep(1000);
    //
    //     int acceptedFirstBatch = 0;
    //     for (int i = 0; i < capacity * 2; i++) {
    //         if (limiter.add()) acceptedFirstBatch++;
    //     }
    //
    //     System.out.println(acceptedFirstBatch);
    // }

}
