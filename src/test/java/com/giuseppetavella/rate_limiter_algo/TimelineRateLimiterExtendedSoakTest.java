package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.timeline.TimelineRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Extended soak test: runs for a configurable long duration, tracking accepted-
 * throughput stability AND heap usage over time, to catch slow degradation or
 * leaks that a 30s run structurally cannot reveal.
 *
 * TO RUN FOR REAL HOURS: change durationMinutes below (e.g. 60, 120) and run
 * directly, not via a CI pipeline with a tight timeout.
 *
 * WHAT THIS CAN'T TELL YOU: behavior under real OS-level resource pressure
 * (throttled CPU, memory pressure from other processes) - run alongside a
 * stress tool (e.g. stress-ng) on a loaded machine for that, separately.
 */
public class TimelineRateLimiterExtendedSoakTest {

    @Test
    @DisplayName("Extended soak: sustained admitted throughput + heap stability over long duration")
    void testExtendedSoakThroughputAndMemory() throws Exception {
        int durationMinutes = 5;   // <-- set to 60, 120, etc. for a real multi-hour run
        int capacity = 1_500_000;
        long windowMs = 1_000;
        int nTimelines = 3;
        int threads = 128;
        int memorySampleIntervalSec = 10;
        int throughputBucketSec = 5;

        long durationSec = durationMinutes * 60L;
        int throughputBuckets = (int) (durationSec / throughputBucketSec);

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(nTimelines).build();
        limiter.start();

        AtomicLong totalAccepted = new AtomicLong(0);
        AtomicLong totalAttempts = new AtomicLong(0);
        AtomicLong[] acceptedPerBucket = new AtomicLong[throughputBuckets];
        for (int i = 0; i < throughputBuckets; i++) acceptedPerBucket[i] = new AtomicLong(0);

        long start = System.currentTimeMillis();
        long end = start + durationSec * 1000L;

        ExecutorService workers = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            workers.submit(() -> {
                while (System.currentTimeMillis() < end) {
                    long elapsed = System.currentTimeMillis() - start;
                    int bucket = Math.min((int) (elapsed / (throughputBucketSec * 1000L)), throughputBuckets - 1);
                    totalAttempts.incrementAndGet();
                    if (limiter.add()) {
                        totalAccepted.incrementAndGet();
                        acceptedPerBucket[bucket].incrementAndGet();
                    }
                }
            });
        }

        // Memory sampler runs concurrently, records heap usage at fixed intervals.
        List<long[]> memorySnapshots = new ArrayList<>(); // {elapsedSec, usedBytes}
        Runtime rt = Runtime.getRuntime();
        ScheduledExecutorService memSampler = Executors.newSingleThreadScheduledExecutor();
        memSampler.scheduleAtFixedRate(() -> {
            long elapsedSec = (System.currentTimeMillis() - start) / 1000;
            long used = rt.totalMemory() - rt.freeMemory();
            synchronized (memorySnapshots) {
                memorySnapshots.add(new long[]{elapsedSec, used});
            }
        }, memorySampleIntervalSec, memorySampleIntervalSec, TimeUnit.SECONDS);

        workers.shutdown();
        workers.awaitTermination(durationSec + 30, TimeUnit.SECONDS);
        memSampler.shutdown();

        System.out.printf("%n=== Extended soak: %d minutes, capacity=%,d/sec ===%n", durationMinutes, capacity);
        long minBucket = Long.MAX_VALUE, maxBucket = 0;
        long sumBucket = 0;
        int nonEmptyBuckets = 0;
        for (int i = 0; i < throughputBuckets; i++) {
            long v = acceptedPerBucket[i].get();
            minBucket = Math.min(minBucket, v);
            maxBucket = Math.max(maxBucket, v);
            sumBucket += v;
            nonEmptyBuckets++;
        }
        double expectedPerBucket = capacity * throughputBucketSec;
        double avgBucket = nonEmptyBuckets > 0 ? (double) sumBucket / nonEmptyBuckets : 0;

        System.out.printf("Expected accepted per %ds bucket: %,.0f%n", throughputBucketSec, expectedPerBucket);
        System.out.printf("Observed: min=%,d  avg=%,.0f  max=%,d (across %d buckets)%n",
                minBucket, avgBucket, maxBucket, nonEmptyBuckets);
        System.out.printf("Total attempts: %,d  Total accepted: %,d%n", totalAttempts.get(), totalAccepted.get());

        double deviationPct = 100.0 * Math.abs(avgBucket - expectedPerBucket) / expectedPerBucket;
        System.out.printf("Average deviation from expected: %.4f%%%n", deviationPct);

        System.out.println("\nMemory usage over time (used heap bytes):");
        long firstUsed = -1, lastUsed = -1;
        synchronized (memorySnapshots) {
            for (long[] snap : memorySnapshots) {
                System.out.printf("  [%4ds]: %,d bytes (%.1f MB)%n", snap[0], snap[1], snap[1] / 1024.0 / 1024.0);
                if (firstUsed == -1) firstUsed = snap[1];
                lastUsed = snap[1];
            }
        }
        if (firstUsed > 0) {
            double growthPct = 100.0 * (lastUsed - firstUsed) / firstUsed;
            System.out.printf("%nHeap growth from first to last sample: %.1f%%%n", growthPct);
            System.out.println("(JVM heap is noisy - GC timing affects any single sample. Look for a " +
                    "consistent upward TREND across many samples, not any single delta, before concluding " +
                    "there's a leak.)");
        }

        if (deviationPct > 0.5) {
            throw new AssertionError(String.format(
                "Average accepted throughput deviated %.4f%% from expected capacity - exceeds 0.5%% tolerance.",
                deviationPct));
        }
    }
}