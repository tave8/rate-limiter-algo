package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.history_queue.HistoryQueue;
import com.giuseppetavella.rate_limiter_algo.timeline.TimelineManager;
import org.junit.jupiter.api.Test;

public class DemoTest {
    @Test
    void test1() throws InterruptedException {
        int maxEvents = 100;
        long window = 1000;
        
        RateLimiter rateLimiter1 = new TimelineManager(maxEvents, window);
        RateLimiter rateLimiter2 = new HistoryQueue(maxEvents, window);
        
        rateLimiter1.after(100);
        
    }
}
