package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.ConcurrentModifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimelineManagerTest {
    @Test
    void test1() throws InterruptedException {
        var manager = new TimelineManager(10, 1000, 1);
        
        new ConcurrentModifier()
            .concurrently(() -> {
                manager.add();
                manager.add();
                manager.add();
                manager.add();
            })
            .concurrently(() -> {
                manager.add();
                manager.add();
                manager.add();
                manager.add();
            })
            .concurrently(() -> {
                manager.add();
                manager.add();
            })
            .concurrently(() -> {
                // manager.add();
            })
            .useRawThreads();
        
        
        Thread.sleep(100000);
    }
}