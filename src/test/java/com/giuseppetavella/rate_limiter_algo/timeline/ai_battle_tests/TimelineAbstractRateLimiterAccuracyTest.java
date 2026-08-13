package com.giuseppetavella.rate_limiter_algo.timeline.ai_battle_tests;

import com.giuseppetavella.rate_limiter_algo.timeline.TimelineRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
            TimelineRateLimiter manager = new TimelineRateLimiter.Builder(maxEventsPerWindow, windowMs).nTimelines(nTimelines)
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
}
