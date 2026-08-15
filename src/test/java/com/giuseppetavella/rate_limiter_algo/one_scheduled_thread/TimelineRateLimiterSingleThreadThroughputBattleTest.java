package com.giuseppetavella.rate_limiter_algo.one_scheduled_thread;

import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.TimelineNThreadsRateLimiter;
import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.TimelineRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ====================================================================================================
 *  BATTLE TEST: RAW PROCESSING THROUGHPUT - single-scheduler-thread design
 * ====================================================================================================
 *
 * Measures raw add() call-handling capacity (attempts/sec, independent of admission),
 * same methodology proven earlier: high capacity so acceptance never becomes the
 * bottleneck, multiple independent trials (not one lucky run), a thread-count sweep
 * (since raw throughput scales with concurrency up to a point, then plateaus or
 * degrades from oversubscription), and a sustained soak to rule out degradation
 * specific to the new tick-chaining scheduler under prolonged load.
 *
 * This isolates whether moving from N scheduler threads to 1 changed the algorithm's
 * raw processing ceiling - the scheduler thread itself now also competes for CPU
 * with worker threads doing add(), which it didn't in the same way before (N threads
 * each tied to one timeline vs 1 thread cycling through all of them).
 */
public class TimelineRateLimiterSingleThreadThroughputBattleTest {

    // ================================================================================
    // TEST 1: MULTI-TRIAL RAW THROUGHPUT AT FIXED THREAD COUNT
    // ================================================================================
    @Test
    @DisplayName("Battle: raw processing throughput, 5 trials, capacity high enough to never bottleneck")
    void testRawThroughputMultiTrial() throws Exception {
        long capacity = 100_000_000L; // deliberately absurd - never let admission become the limiter
        long windowMs = 10_000;        // long window: minimize reset overhead skewing this measurement
        int nTimelines = 3;
        int threads = 64;
        int trials = 5;
        int durationSec = 10;

        List<Double> throughputs = new ArrayList<>();

        for (int trial = 1; trial <= trials; trial++) {
            var limiter = new TimelineRateLimiter.Builder((int) Math.min(capacity, Integer.MAX_VALUE), windowMs)
                    .nTimelines(nTimelines).build();
            limiter.start();

            AtomicLong totalAttempts = new AtomicLong(0);
            long start = System.currentTimeMillis();
            long end = start + durationSec * 1000L;

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    while (System.currentTimeMillis() < end) {
                        limiter.add();
                        totalAttempts.incrementAndGet();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(durationSec + 15, TimeUnit.SECONDS);
            limiter.stop();

            long actualDurationMs = System.currentTimeMillis() - start;
            double throughput = totalAttempts.get() / (actualDurationMs / 1000.0);
            throughputs.add(throughput);
            System.out.printf("Trial %d: %,.0f calls/sec (%,d total attempts over %.1fs)%n",
                    trial, throughput, totalAttempts.get(), actualDurationMs / 1000.0);
        }

        double min = throughputs.stream().mapToDouble(d -> d).min().orElse(0);
        double max = throughputs.stream().mapToDouble(d -> d).max().orElse(0);
        double avg = throughputs.stream().mapToDouble(d -> d).average().orElse(0);
        double spreadPct = 100.0 * (max - min) / avg;

        System.out.printf("%nMin: %,.0f  Avg: %,.0f  Max: %,.0f calls/sec  (spread: %.1f%% of avg)%n",
                min, avg, max, spreadPct);
        System.out.println("(High spread across trials would indicate the single scheduler thread " +
                "introduces more run-to-run variance than the old N-thread design - worth comparing " +
                "against historical N-thread numbers if you have them.)");
    }

    // ================================================================================
    // TEST 2: THREAD-COUNT SWEEP - does the single scheduler thread become a
    // bottleneck or contention point as worker thread count scales up?
    // ================================================================================
    @Test
    @DisplayName("Battle: throughput across thread counts (1 to 256) - single scheduler thread scaling")
    void testThroughputAcrossThreadCounts() throws Exception {
        int capacity = Integer.MAX_VALUE;
        long windowMs = 10_000;
        int nTimelines = 3;
        int durationSec = 5;
        int[] threadCounts = {1, 4, 8, 16, 32, 64, 128, 256};

        System.out.println("\nThreads | Calls/sec");
        System.out.println("--------|----------");
        for (int threads : threadCounts) {
            TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                    .nTimelines(nTimelines).build();
            limiter.start();

            AtomicLong totalAttempts = new AtomicLong(0);
            long start = System.currentTimeMillis();
            long end = start + durationSec * 1000L;

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    while (System.currentTimeMillis() < end) {
                        limiter.add();
                        totalAttempts.incrementAndGet();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(durationSec + 15, TimeUnit.SECONDS);
            limiter.stop();

            long actualDurationMs = System.currentTimeMillis() - start;
            double throughput = totalAttempts.get() / (actualDurationMs / 1000.0);
            System.out.printf(" %-6d | %,.0f%n", threads, throughput);
        }
        System.out.println("\n(Look for: does throughput plateau/decline past your machine's core count, " +
                "same as the old design did? A single scheduler thread pinned to CPU work during " +
                "ticks could theoretically create a contention hotspot the old N-thread design didn't " +
                "have - this sweep is how you'd see that show up, if it exists.)");
    }

    // ================================================================================
    // TEST 3: SUSTAINED SOAK - does raw throughput hold over minutes, specifically
    // under the new tick-chaining scheduler (not just a 5-10s snapshot)?
    // ================================================================================
    @Test
    @DisplayName("Battle: sustained raw throughput over 2 minutes, single-thread-tick design")
    void testSustainedRawThroughputSoak() throws Exception {
        int capacity = Integer.MAX_VALUE;
        long windowMs = 10_000;
        int nTimelines = 3;
        int threads = 64;
        int durationSec = 120;
        int bucketSec = 10;

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(nTimelines).build();
        limiter.start();

        int numBuckets = durationSec / bucketSec;
        AtomicLong[] attemptsPerBucket = new AtomicLong[numBuckets];
        for (int i = 0; i < numBuckets; i++) attemptsPerBucket[i] = new AtomicLong(0);

        long start = System.currentTimeMillis();
        long end = start + durationSec * 1000L;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                while (System.currentTimeMillis() < end) {
                    long elapsed = System.currentTimeMillis() - start;
                    int bucket = Math.min((int) (elapsed / (bucketSec * 1000L)), numBuckets - 1);
                    limiter.add();
                    attemptsPerBucket[bucket].incrementAndGet();
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(durationSec + 30, TimeUnit.SECONDS);
        limiter.stop();

        System.out.println("\nBucket (sec) | Calls/sec in bucket");
        double firstBucketRate = -1, lastBucketRate = -1;
        for (int i = 0; i < numBuckets; i++) {
            double rate = attemptsPerBucket[i].get() / (double) bucketSec;
            System.out.printf("  [%3d-%3d]  | %,.0f%n", i * bucketSec, (i + 1) * bucketSec, rate);
            if (i == 0) firstBucketRate = rate;
            lastBucketRate = rate;
        }

        double degradationPct = 100.0 * (1.0 - lastBucketRate / firstBucketRate);
        System.out.printf("%nDegradation from first to last bucket: %.1f%%%n", degradationPct);

        if (degradationPct > 25) {
            throw new AssertionError(String.format(
                "Raw throughput degraded %.1f%% from start to end of a %ds soak - the single " +
                "scheduler thread design may accumulate overhead or contention over time that " +
                "the old N-thread design did not exhibit.", degradationPct, durationSec));
        }
        System.out.println("PASSED: no significant sustained-load degradation.");
    }
}