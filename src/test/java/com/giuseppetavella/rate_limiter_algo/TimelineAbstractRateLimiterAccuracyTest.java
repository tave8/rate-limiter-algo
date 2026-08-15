package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.TimelineNThreadsRateLimiter;
import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.TimelineRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ====================================================================================================
 *                                  BATTLE TEST 1: STATISTICAL SLIDING WINDOW ACCURACY
 * ====================================================================================================
 *
 * PURPOSE OF THIS TEST:
 * ---------------------
 * Evaluates the statistical accuracy of the TimelineManager's staggered multi-timeline algorithm
 * under sustained high-volume traffic overload (3x max quota limit).
 *
 * HOW THE TEST WORKS:
 * -------------------
 * 1. Sets a limit of 1,000 requests per 1,000ms window across a 3-second test duration (Expected limit = 3,000 total).
 * 2. Drives a continuous overload stream of ~14,000 requests using 8 parallel worker threads.
 * 3. Evaluates total accepted requests vs ideal expected capacity for N = 1, 2, 4, 8, 16 timelines.
 *
 * PRACTICAL MEANING OF RESULTS:
 * -----------------------------
 * - Demonstrates how staggering N timelines approximates a continuous sliding log window.
 * - Measures whether the rate limiter strictly enforces quotas or permits statistical drift.
 * - Formula: Accuracy % = 100% - (|Accepted - Expected| / Expected * 100%)
 *
 * @author AI Battle-Test Suite
 */
public class TimelineAbstractRateLimiterAccuracyTest {

    @Test
    @DisplayName("Battle-Test: Statistical Sliding Window Accuracy (N = 1, 2, 4, 8, 16)")
    void testSlidingWindowAccuracyAcrossTimelines() throws InterruptedException {
        int maxEventsPerWindow = 1_000;
        long windowMs = 1_000;
        int testDurationSec = 3;
        int expectedAcceptedTotal = maxEventsPerWindow * testDurationSec; // 3,000 requests ideally

        int[] timelineCounts = {1, 2, 4, 8, 16};

        System.out.println("\n==========================================================================================");
        System.out.println("                   BATTLE-TEST 1: STATISTICAL SLIDING WINDOW ACCURACY                     ");
        System.out.println("==========================================================================================");
        System.out.println("Scenario: Driving continuous overload stream (3x max limit) across 8 parallel threads.");
        System.out.println("Goal: Measure how closely staggered multi-timelines approximate sliding window capacity.");
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.printf("%-14s | %-12s | %-10s | %-10s | %-10s | %-12s%n",
                "Timelines (N)", "Limit/Sec", "Attempted", "Accepted", "Expected", "Accuracy %");
        System.out.println("------------------------------------------------------------------------------------------");

        for (int nTimelines : timelineCounts) {
            TimelineNThreadsRateLimiter manager = new TimelineNThreadsRateLimiter.Builder(maxEventsPerWindow, windowMs).nTimelines(nTimelines)
                    .build();
            manager.start();

            AtomicInteger totalAccepted = new AtomicInteger(0);
            AtomicInteger totalAttempted = new AtomicInteger(0);

            int numThreads = 8;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            long startTime = System.currentTimeMillis();
            long endTime = startTime + (testDurationSec * 1000L);

            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    while (System.currentTimeMillis() < endTime) {
                        totalAttempted.incrementAndGet();
                        if (manager.add()) {
                            totalAccepted.incrementAndGet();
                        }
                        try {
                            TimeUnit.MICROSECONDS.sleep(800);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(testDurationSec + 2, TimeUnit.SECONDS);

            int accepted = totalAccepted.get();
            int attempted = totalAttempted.get();

            double error = Math.abs(accepted - expectedAcceptedTotal) / (double) expectedAcceptedTotal;
            double accuracyPct = Math.max(0, (1.0 - error) * 100.0);

            System.out.printf(" %-13d | %-12d | %-10d | %-10d | %-10d | %6.2f%%%n",
                    nTimelines, maxEventsPerWindow, attempted, accepted, expectedAcceptedTotal, accuracyPct);
        }

        System.out.println("==========================================================================================\n");
    }

    /**
     * Measures actual ADMITTED throughput (accepted events/sec) under sustained load,
     * as opposed to raw call throughput (attempts/sec, which includes rejections).
     *
     * Configure the limiter's capacity to whatever real ceiling you want to validate -
     * this test answers "does it actually admit that many per second, sustained."
     */
    public static class TimelineRateLimiterAdmittedThroughputTest {
    
        @Test
        @DisplayName("Sustained admitted throughput (accepted/sec) over 30s at real capacity")
        void testSustainedAdmittedThroughput() throws Exception {
            int capacity = 1_500_000;   // set to whatever your real target ceiling is
            long windowMs = 1_000;       // 1-second window -> capacity IS your target events/sec
            int nTimelines = 3;
            int threads = 16;
            int soakSeconds = 30;
            int bucketSeconds = 1;       // per-second buckets so you see the actual sustained rate directly
    
            TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                    .nTimelines(nTimelines).build();
            limiter.start();
    
            AtomicLong[] acceptedPerBucket = new AtomicLong[soakSeconds];
            for (int i = 0; i < soakSeconds; i++) acceptedPerBucket[i] = new AtomicLong(0);
            AtomicLong totalAccepted = new AtomicLong(0);
            AtomicLong totalAttempts = new AtomicLong(0);
    
            long start = System.currentTimeMillis();
            long end = start + soakSeconds * 1000L;
    
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    while (System.currentTimeMillis() < end) {
                        long elapsed = System.currentTimeMillis() - start;
                        int bucket = Math.min((int) (elapsed / (bucketSeconds * 1000L)), soakSeconds - 1);
                        totalAttempts.incrementAndGet();
                        if (limiter.add()) {
                            totalAccepted.incrementAndGet();
                            acceptedPerBucket[bucket].incrementAndGet();
                        }
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(soakSeconds + 15, TimeUnit.SECONDS);
    
            System.out.println("\n=== Admitted throughput per " + bucketSeconds + "s bucket ===");
            long minAccepted = Long.MAX_VALUE, maxAccepted = 0;
            for (int i = 0; i < soakSeconds; i++) {
                long v = acceptedPerBucket[i].get();
                minAccepted = Math.min(minAccepted, v);
                maxAccepted = Math.max(maxAccepted, v);
                System.out.printf("  [%2ds]: %,d accepted%n", i, v);
            }
    
            double acceptRate = 100.0 * totalAccepted.get() / totalAttempts.get();
            System.out.printf("%nConfigured capacity/sec: %,d%n", capacity);
            System.out.printf("Min accepted in a 1s bucket: %,d%n", minAccepted);
            System.out.printf("Max accepted in a 1s bucket: %,d%n", maxAccepted);
            System.out.printf("Total attempts: %,d  Total accepted: %,d (%.2f%% accept rate)%n",
                    totalAttempts.get(), totalAccepted.get(), acceptRate);
    
            // The real claim you want to defend: does every full second actually admit
            // close to the configured capacity, sustained, not just once?
            if (maxAccepted > capacity * 1.005) {
                throw new AssertionError(String.format(
                    "A single bucket admitted %,d, exceeding capacity %,d by more than 0.5%%.",
                    maxAccepted, capacity));
            }
        }
    }
}
