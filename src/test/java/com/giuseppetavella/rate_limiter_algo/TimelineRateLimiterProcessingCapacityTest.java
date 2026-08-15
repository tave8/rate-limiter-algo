package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.timeline.TimelineRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Measures raw PROCESSING capacity (calls/sec handled), independent of admission
 * rate. This answers "how many events can add() actually be called on per second"
 * - not "how many get accepted," which depends on the filter/capacity config.
 *
 * Runs the same test twice: once with the default (always-true) filterer, once
 * with a custom filterer attached, to isolate the filter's cost on raw throughput.
 */
public class TimelineRateLimiterProcessingCapacityTest {

    @Test
    @DisplayName("Raw processing capacity: default filterer vs custom filterer")
    void testProcessingCapacityWithAndWithoutFilter() throws Exception {
        int capacity = 1_500_000;
        long windowMs = 1_000; // long window: avoid resets mid-test skewing filter thresholds
        int nTimelines = 3;
        int threads = 64;
        int durationSec = 10;

        System.out.println("=== WITHOUT custom filter (default: always true) ===");
        long noFilterAttempts = runProcessingTest(
                new TimelineRateLimiter.Builder(capacity, windowMs).nTimelines(nTimelines).build(),
                threads, durationSec
        );

        System.out.println("\n=== WITH custom filter ===");
        TimelineRateLimiter filteredLimiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(nTimelines)
                .eventFilterer(t -> {
                    if (t.isBeforeWindowThreshold(.8)) {
                        return t.isBeforeEventThreshold(.9);
                    }
                    return t.isBeforeEventThreshold(.95);
                })
                .build();
        long filteredAttempts = runProcessingTest(filteredLimiter, threads, durationSec);

        double deltaPct = 100.0 * (noFilterAttempts - filteredAttempts) / noFilterAttempts;
        System.out.printf("%n=== Comparison ===%n");
        System.out.printf("Without filter: %,d attempts/sec%n", noFilterAttempts / durationSec);
        System.out.printf("With filter:    %,d attempts/sec%n", filteredAttempts / durationSec);
        System.out.printf("Filter overhead: %.2f%% reduction in raw processing capacity%n", deltaPct);
    }

    private long runProcessingTest(TimelineRateLimiter limiter, int threads, int durationSec) throws Exception {
        limiter.start();
        AtomicLong totalAttempts = new AtomicLong(0);
        long start = System.currentTimeMillis();
        long end = start + durationSec * 1000L;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                while (System.currentTimeMillis() < end) {
                    limiter.add(); // result ignored - we only care how many calls happened
                    totalAttempts.incrementAndGet();
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(durationSec + 15, TimeUnit.SECONDS);

        long actualDurationMs = System.currentTimeMillis() - start;
        double attemptsPerSec = totalAttempts.get() / (actualDurationMs / 1000.0);
        System.out.printf("Total attempts: %,d over %.1fs = %,.0f attempts/sec%n",
                totalAttempts.get(), actualDurationMs / 1000.0, attemptsPerSec);
        return totalAttempts.get();
    }
}