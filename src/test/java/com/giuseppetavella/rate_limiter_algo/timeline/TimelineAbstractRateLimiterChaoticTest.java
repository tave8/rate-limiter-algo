package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.core.timeline.EventFilterer;
import com.giuseppetavella.rate_limiter_algo.core.timeline.TimelineRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class TimelineAbstractRateLimiterChaoticTest {

    @Test
    void testChaoticConcurrencyOnTimelineManager() throws InterruptedException {
        int maxEvents = 1_000;
        int window = 1000;
        int nTimelines = 3;


        EventFilterer fil = (t) -> { 
            if(t.isBeforeWindowThreshold(.8)) {
                return t.isBeforeEventThreshold(.95);
            }
            return t.isBeforeEventThreshold(.97);
        };

        var manager = new TimelineRateLimiter.Builder(maxEvents, window)
                .nTimelines(nTimelines)
                .eventFilterer(fil)
                .verbose(true)
                .build();

        int poolSize = 16;
        int burstThreads = 8;
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(poolSize);
        ExecutorService burstExecutor = Executors.newFixedThreadPool(burstThreads);

        AtomicInteger totalAttemptedAdds = new AtomicInteger(0);

        // 1. Launch dynamic, randomized scheduled tasks
        for (int i = 0; i < 5; i++) {
            scheduleChaoticTask(scheduler, manager, totalAttemptedAdds);
        }

        // 2. Launch synchronized barrier bursts (forces simultaneous collisions)
        scheduleBurstCollisions(scheduler, burstExecutor, manager, burstThreads, totalAttemptedAdds);

        // Run the chaotic load for 10 seconds (adjust as needed)
        Thread.sleep(Duration.ofSeconds(20).toMillis());

        // Clean up thread pools
        scheduler.shutdownNow();
        burstExecutor.shutdownNow();

        System.out.println("Stress test completed.");
        System.out.println("Total manager.add() calls attempted: " + totalAttemptedAdds.get());
    }

    private void scheduleChaoticTask(ScheduledExecutorService scheduler, TimelineRateLimiter manager, AtomicInteger totalAttemptedAdds) {
        long nextDelayMs = ThreadLocalRandom.current().nextLong(5, 150);

        scheduler.schedule(() -> {
            try {
                // Fluctuating payload size
                int sigma = ThreadLocalRandom.current().nextInt(-30, 20);
                int eventsToRun = Math.max(0, 10 + sigma);

                for (int i = 0; i < eventsToRun; i++) {
                    manager.add();
                    totalAttemptedAdds.incrementAndGet();

                    // Force context switch on ~15% of iterations to disrupt execution order
                    if (ThreadLocalRandom.current().nextInt(100) < 15) {
                        Thread.yield();
                    }
                }
            } catch (RuntimeException ex) {
                System.out.println("Exception caught: " + ex.getMessage());
            } finally {
                // Self-reschedule with a dynamic random delay
                scheduleChaoticTask(scheduler, manager, totalAttemptedAdds);
            }
        }, nextDelayMs, TimeUnit.MILLISECONDS);
    }

    private void scheduleBurstCollisions(ScheduledExecutorService scheduler, ExecutorService burstExecutor,
                                         TimelineRateLimiter manager, int burstThreads, AtomicInteger totalAttemptedAdds) {
        
        scheduler.scheduleAtFixedRate(() -> {
            CyclicBarrier barrier = new CyclicBarrier(burstThreads);

            for (int i = 0; i < burstThreads; i++) {
                burstExecutor.submit(() -> {
                    try {
                        // Align all threads to release at the exact same nanosecond
                        barrier.await();

                        int events = ThreadLocalRandom.current().nextInt(5, 25);
                        for (int j = 0; j < events; j++) {
                            manager.add();
                            totalAttemptedAdds.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                        // Handle barrier resets or interrupts gracefully
                    }
                });
            }
        }, 100, 250, TimeUnit.MILLISECONDS);
    }
}