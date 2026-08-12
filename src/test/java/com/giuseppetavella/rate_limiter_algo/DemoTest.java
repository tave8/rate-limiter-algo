package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.history_queue.HistoryQueue;
import com.giuseppetavella.rate_limiter_algo.timeline.TimelineManager;
import org.junit.jupiter.api.Test;

public class DemoTest {
    @Test
    void test1() throws InterruptedException {
        int maxEvents = 100;
        long window = 1000;
        
        Clock testClock = new ClockImpl();
        Clock clock1 = new ClockImpl();
        Clock clock2 = new ClockImpl();         
        
        RateLimiter rateLimiter1 = new TimelineManager.Builder(maxEvents, window, 1)
                .build();
        
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
}
