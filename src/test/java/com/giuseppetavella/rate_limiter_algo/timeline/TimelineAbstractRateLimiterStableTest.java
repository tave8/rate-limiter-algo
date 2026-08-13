package com.giuseppetavella.rate_limiter_algo.timeline;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

class TimelineAbstractRateLimiterStableTest {

    @Test
    void testSteadyStateTraffic() throws InterruptedException {
        int maxEvents = 100;
        int windowMs = 1000;
        int nTimelines = 3;


        // EventFilterer fil = (t) -> {
        //     if(t.isBeforeWindowThreshold(.8)) {
        //         return t.isBeforeEventThreshold(.95);
        //     }
        //     return true;
        // };

        var manager = new TimelineRateLimiter.Builder(maxEvents, windowMs).nTimelines(nTimelines)
                .verbose(true)
                .build();

        AtomicLong attempted = new AtomicLong(0);
        AtomicLong accepted = new AtomicLong(0);
        AtomicLong rejected = new AtomicLong(0);

        // Target: 150 events/sec uniformly paced (1 event every ~6.6ms)
        int targetEventsPerSecond = 150;
        long intervalMicros = 1_000_000L / targetEventsPerSecond;

        ScheduledExecutorService pacer = Executors.newSingleThreadScheduledExecutor();
        ExecutorService workerPool = Executors.newFixedThreadPool(4);

        int testDurationSeconds = 5;

        pacer.scheduleAtFixedRate(() -> {
            workerPool.submit(() -> {
                attempted.incrementAndGet();
                try {
                    manager.add(); // ONLY METHOD CALLED
                    accepted.incrementAndGet();
                } catch (Exception ex) { 
                    // Catches your rate limit exceptions when the window fills
                    // rejected.incrementAndGet();
                    System.out.println(ex);
                } 
            });
        }, 0, intervalMicros, TimeUnit.MICROSECONDS);

        Thread.sleep(Duration.ofSeconds(testDurationSeconds).toMillis());

        pacer.shutdownNow();
        workerPool.shutdownNow();

        // System.out.println("=== STABLE TEST RESULTS ===");
        // System.out.println("Total Attempted : " + attempted.get());
        // System.out.println("Total Accepted  : " + accepted.get());
        // System.out.println("Total Rejected  : " + rejected.get());

        // --- ASSERTIONS ---
        
        // 1. Invariant
        // assertEquals(attempted.get(), accepted.get() + rejected.get(),
        //     "Attempted must equal accepted + rejected");
        //
        // // 2. Hard Capacity Bound
        // long maxCapacity = (long) maxEvents * testDurationSeconds;
        // assertTrue(accepted.get() <= maxCapacity,
        //     "Accepted (" + accepted.get() + ") exceeded max capacity (" + maxCapacity + ")");
        //
        // // 3. Steady Load Efficiency
        // assertTrue(accepted.get() >= maxCapacity * 0.85,
        //     "Under smooth load, accepted count was lower than expected");
    }
}