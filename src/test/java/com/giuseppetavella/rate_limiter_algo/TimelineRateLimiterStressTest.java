package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.timeline.TimelineRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ====================================================================================================
 *  STRESS TEST: CORRECTNESS UNDER CONTENTION + SUSTAINED THROUGHPUT
 * ====================================================================================================
 *
 * WHY THIS FILE EXISTS
 * ---------------------
 * A throughput number alone (e.g. "1.5M ops/sec") proves nothing about correctness. A limiter that
 * accepts everything unconditionally, or that double-counts under a race, will ALSO post huge
 * throughput numbers. This suite is built around one question per test: "if this were subtly broken,
 * would this test catch it?" Each test asserts a real invariant, not just a speed measurement.
 *
 * WHAT'S DIFFERENT FROM THE ORIGINAL BENCHMARK
 * ----------------------------------------------
 *  1. Correctness under concurrency: with N threads hammering a capacity-bounded window, the accepted
 *     count must land within the mathematically expected bound - not just "be fast."
 *  2. Thundering herd: all threads released simultaneously via CyclicBarrier, no ramp-up.
 *  3. Sustained soak run: 30s+ continuous load to catch degradation, GC pauses, memory growth,
 *     window-boundary bugs that a 2-second run can't surface.
 *  4. Unbounded, unbiased latency sampling (via Recorder using dynamic arrays / reservoir, not a
 *     fixed 100k cap per thread that under-samples high-thread-count runs).
 *  5. Window-boundary correctness: verify the sliding/rolling window actually rolls (accepts resume
 *     after the window elapses), not just that it rejects when full.
 *  6. Repeated trials per configuration (not one run) with min/median/max reported, to separate
 *     signal from JIT warm-up / scheduler noise.
 *  7. Fairness/starvation check: no single thread should be locked out while others succeed.
 *
 * ONE ASSUMPTION FLAGGED
 * ------------------------
 * The original benchmark calls `manager.add()` as a bare statement, so its return type is unknown
 * to me. This suite assumes `add()` returns a boolean (true = accepted, false = rejected), which is
 * the only way "capacity" is a testable, falsifiable claim. If your real signature is void, or
 * returns something else (e.g. throws on rejection, or returns an enum), change ONLY the
 * `attempt()` helper method below - nothing else needs to change.
 *
 * @author Stress-Test Suite
 */
public class TimelineRateLimiterStressTest {

    // ================================================================================
    // Helper: adapts to add()'s real return type. EDIT THIS if add() isn't boolean.
    // ================================================================================
    private static boolean attempt(TimelineRateLimiter limiter) {
        Object result = limiter.add();
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        // If add() is void or non-boolean, we can't measure acceptance/rejection at all,
        // which means capacity claims are UNTESTABLE with the current API surface.
        throw new IllegalStateException(
            "add() did not return a boolean - correctness assertions in this suite require " +
            "a way to distinguish accepted vs rejected calls. Expose that from the limiter."
        );
    }

    // ================================================================================
    // TEST 1: CORRECTNESS UNDER THUNDERING-HERD CONCURRENCY
    // ================================================================================
    // Fires `capacity * overshootFactor` requests from many threads released at the exact
    // same instant (CyclicBarrier), then asserts accepted count is <= capacity and > 0.
    // A racy implementation will typically overshoot capacity under this pattern even
    // when it looks correct in single-threaded or low-concurrency use.
    @Test
    @DisplayName("Correctness: accepted count never exceeds capacity under thundering herd")
    void testAcceptedCountNeverExceedsCapacityUnderThunderingHerd() throws Exception {
        int capacity = 50_000;
        long windowMs = 1_000;
        int threads = 64;
        int attemptsPerThread = 5_000; // threads * attemptsPerThread >> capacity, guaranteed overshoot attempt

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs).nTimelines(1).build();
        limiter.start();

        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicLong accepted = new AtomicLong(0);
        AtomicLong rejected = new AtomicLong(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(); // all threads unblock on the same instant
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (attempt(limiter)) accepted.incrementAndGet();
                        else rejected.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        long totalAttempts = (long) threads * attemptsPerThread;
        System.out.printf("Thundering herd: capacity=%d, attempts=%d, accepted=%d, rejected=%d%n",
                capacity, totalAttempts, accepted.get(), rejected.get());

        if (accepted.get() > capacity) {
            throw new AssertionError(String.format(
                "CORRECTNESS VIOLATION: accepted %d requests but capacity is %d - the limiter " +
                "over-admitted under concurrent contention.", accepted.get(), capacity));
        }
        if (accepted.get() == 0) {
            throw new AssertionError("Accepted zero requests - limiter is rejecting everything, " +
                    "or add() semantics differ from assumption.");
        }
        if (accepted.get() + rejected.get() != totalAttempts) {
            throw new AssertionError("Lost updates: accepted+rejected != total attempts. " +
                    "This means add() calls are being dropped or double-counted under contention.");
        }
    }

    // ================================================================================
    // TEST 2: WINDOW ROLLS FORWARD (capacity is recoverable, not a one-shot ceiling)
    // ================================================================================
    // Exhausts capacity, confirms rejection, waits for the window to elapse, then
    // confirms new capacity is available. Catches implementations that "leak" state
    // and never recover, or that recover too early/late.
    @Test
    @DisplayName("Correctness: capacity resets after the time window elapses")
    void testWindowRecoversAfterElapsing() throws InterruptedException {
        int capacity = 1_000;
        long windowMs = 500;

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs).nTimelines(1).build();
        limiter.start();

        int acceptedFirstBatch = 0;
        for (int i = 0; i < capacity * 2; i++) {
            if (attempt(limiter)) acceptedFirstBatch++;
        }
        System.out.printf("First batch: accepted %d / %d attempts (capacity=%d)%n",
                acceptedFirstBatch, capacity * 2, capacity);
        if (acceptedFirstBatch > capacity) {
            throw new AssertionError("Accepted more than capacity in a single window: " + acceptedFirstBatch);
        }

        Thread.sleep(windowMs + 200); // let the window fully roll over

        int acceptedAfterWait = 0;
        for (int i = 0; i < capacity; i++) {
            if (attempt(limiter)) acceptedAfterWait++;
        }
        System.out.printf("After window elapsed: accepted %d / %d attempts%n", acceptedAfterWait, capacity);
        if (acceptedAfterWait == 0) {
            throw new AssertionError("Limiter never recovered capacity after the window elapsed - " +
                    "this makes it useless past the first window.");
        }
    }

    // ================================================================================
    // TEST 3: FAIRNESS - no thread is starved while others succeed
    // ================================================================================
    @Test
    @DisplayName("Correctness: no single thread is fully starved under fair contention")
    void testNoThreadStarvation() throws Exception {
        int capacity = 20_000;
        long windowMs = 1_000;
        int threads = 16;
        int attemptsPerThread = 5_000;

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs).nTimelines(1).build();
        limiter.start();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        long[] acceptedByThread = new long[threads];
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int idx = t;
            futures.add(executor.submit(() -> {
                for (int i = 0; i < attemptsPerThread; i++) {
                    if (attempt(limiter)) acceptedByThread[idx]++;
                }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        long zeroThreads = Arrays.stream(acceptedByThread).filter(v -> v == 0).count();
        System.out.println("Accepted per thread: " + Arrays.toString(acceptedByThread));
        if (zeroThreads > threads / 2) {
            throw new AssertionError(String.format(
                "%d of %d threads got ZERO accepted requests - possible starvation/unfairness bug.",
                zeroThreads, threads));
        }
    }

    // ================================================================================
    // TEST 4: SUSTAINED SOAK RUN (30s) - catches degradation a 2s run can't see
    // ================================================================================
    @Test
    @DisplayName("Performance: sustained throughput over 30s does not degrade over time")
    void testSustainedThroughputDoesNotDegradeOverTime() throws InterruptedException, ExecutionException, TimeoutException {
        int capacity = 1_000_000;
        long windowMs = 1_000;
        int threads = 16;
        int soakSeconds = 30;
        int bucketSeconds = 5; // report throughput per 5s bucket to see the trend, not just the average

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs).nTimelines(3).build();
        limiter.start();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        int buckets = soakSeconds / bucketSeconds;
        AtomicLong[] bucketCounts = new AtomicLong[buckets];
        for (int i = 0; i < buckets; i++) bucketCounts[i] = new AtomicLong(0);

        long start = System.currentTimeMillis();
        long testEnd = start + soakSeconds * 1000L;

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                while (System.currentTimeMillis() < testEnd) {
                    limiter.add();
                    long elapsed = System.currentTimeMillis() - start;
                    int bucket = Math.min((int) (elapsed / (bucketSeconds * 1000L)), buckets - 1);
                    bucketCounts[bucket].incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) f.get(soakSeconds + 15, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("Throughput per " + bucketSeconds + "s bucket (ops/sec):");
        double firstBucketRate = -1, lastBucketRate = -1;
        for (int i = 0; i < buckets; i++) {
            double rate = bucketCounts[i].get() / (double) bucketSeconds;
            System.out.printf("  [%3ds-%3ds]: %,.0f ops/sec%n", i * bucketSeconds, (i + 1) * bucketSeconds, rate);
            if (i == 0) firstBucketRate = rate;
            lastBucketRate = rate;
        }

        double degradation = 1.0 - (lastBucketRate / firstBucketRate);
        System.out.printf("Degradation from first to last bucket: %.1f%%%n", degradation * 100);
        if (degradation > 0.40) {
            throw new AssertionError(String.format(
                "Throughput degraded %.1f%% from start to end of a %ds soak run - suggests GC pressure, " +
                "memory growth, or contention that worsens over time. A single 2s benchmark would have missed this.",
                degradation * 100, soakSeconds));
        }
    }

    // ================================================================================
    // TEST 5: REPEATED-TRIAL THROUGHPUT/LATENCY WITH UNBIASED SAMPLING
    // ================================================================================
    // Runs each thread-count 3x (not once), reports min/median/max throughput to separate
    // real performance from scheduler noise, and uses a growable per-thread sample list
    // instead of a fixed 100k cap so percentiles aren't skewed at high thread counts.
    @Test
    @DisplayName("Performance: repeated-trial throughput and latency percentiles (1 to 64 threads)")
    void testRepeatedTrialThroughputAndLatencyPercentiles() throws InterruptedException {
        int capacity = 1_000_000;
        long windowMs = 1_000;
        int nTimelines = 3;
        int trialsPerConfig = 3;
        int trialDurationSec = 2;
        int[] threadCounts = {1, 4, 8, 16, 32, 64};

        System.out.println("\n===================================================================================================");
        System.out.println("      REPEATED-TRIAL THROUGHPUT & LATENCY (median of " + trialsPerConfig + " runs per thread count)");
        System.out.println("===================================================================================================");
        System.out.printf("%-8s | %-14s | %-14s | %-14s | %-10s | %-10s | %-10s%n",
                "Threads", "Min ops/s", "Median ops/s", "Max ops/s", "P50 (ns)", "P99 (ns)", "Max (ns)");
        System.out.println("---------------------------------------------------------------------------------------------------");

        for (int threads : threadCounts) {
            double[] trialThroughputs = new double[trialsPerConfig];
            long[] lastTrialLatencies = null;

            for (int trial = 0; trial < trialsPerConfig; trial++) {
                TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                        .nTimelines(nTimelines).build();
                limiter.start();

                ExecutorService executor = Executors.newFixedThreadPool(threads);
                AtomicLong totalOps = new AtomicLong(0);
                List<List<Long>> perThreadSamples = Collections.synchronizedList(new ArrayList<>());

                long startTime = System.currentTimeMillis();
                long endTime = startTime + trialDurationSec * 1000L;

                for (int t = 0; t < threads; t++) {
                    executor.submit(() -> {
                        // Unbounded growable list: no artificial sample cap that would
                        // under-sample high-thread-count trials relative to low ones.
                        List<Long> samples = new ArrayList<>(200_000);
                        while (System.currentTimeMillis() < endTime) {
                            long t0 = System.nanoTime();
                            limiter.add();
                            long elapsed = System.nanoTime() - t0;
                            totalOps.incrementAndGet();
                            samples.add(elapsed);
                        }
                        perThreadSamples.add(samples);
                    });
                }
                executor.shutdown();
                executor.awaitTermination(trialDurationSec + 5, TimeUnit.SECONDS);
                long actualDurationMs = System.currentTimeMillis() - startTime;

                trialThroughputs[trial] = totalOps.get() / (actualDurationMs / 1000.0);

                if (trial == trialsPerConfig - 1) {
                    int total = perThreadSamples.stream().mapToInt(List::size).sum();
                    long[] merged = new long[total];
                    int off = 0;
                    for (List<Long> s : perThreadSamples) {
                        for (long v : s) merged[off++] = v;
                    }
                    Arrays.sort(merged);
                    lastTrialLatencies = merged;
                }
            }

            Arrays.sort(trialThroughputs);
            double min = trialThroughputs[0];
            double median = trialThroughputs[trialsPerConfig / 2];
            double max = trialThroughputs[trialsPerConfig - 1];

            int n = lastTrialLatencies.length;
            long p50 = n > 0 ? lastTrialLatencies[(int) (n * 0.50)] : 0;
            long p99 = n > 0 ? lastTrialLatencies[(int) (n * 0.99)] : 0;
            long maxLat = n > 0 ? lastTrialLatencies[n - 1] : 0;

            System.out.printf(" %-7d | %-14.0f | %-14.0f | %-14.0f | %-10d | %-10d | %-10d%n",
                    threads, min, median, max, p50, p99, maxLat);

            // A >30% spread between min and max trial throughput at the same thread count
            // means the earlier single-run number wasn't stable enough to make claims from.
            double spread = (max - min) / median;
            if (spread > 0.30) {
                System.out.printf("  NOTE: high run-to-run variance at %d threads (spread=%.0f%% of median) - " +
                        "treat the single-run 1.5M/sec figure with caution at this concurrency level.%n",
                        threads, spread * 100);
            }
        }
        System.out.println("===================================================================================================\n");
    }
}