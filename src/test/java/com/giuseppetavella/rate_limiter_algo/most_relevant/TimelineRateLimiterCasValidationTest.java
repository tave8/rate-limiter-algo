package com.giuseppetavella.rate_limiter_algo.most_relevant;

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
 * Validates the CAS-based add() fix: exact boundary correctness, canAdd()/add()
 * consistency (fixed to actually exceed capacity), and latency specifically in the
 * last portion of capacity where CAS retries concentrate.
 */
public class TimelineRateLimiterCasValidationTest {

    // ================================================================================
    // TEST 1: EXACT BOUNDARY - SINGLE THREAD
    // ================================================================================
    // No contention at all. If this fails, the bug is in the logic itself, not the race.
    @Test
    @DisplayName("Boundary: single-threaded, attempts == capacity exactly")
    void testExactBoundarySingleThread() {
        int capacity = 10_000;
        long windowMs = 5_000;

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(1).build();
        limiter.start();

        int accepted = 0;
        for (int i = 0; i < capacity; i++) {
            if (limiter.add()) accepted++;
        }

        System.out.printf("Single-thread exact boundary: accepted %d / %d attempts%n", accepted, capacity);
        if (accepted != capacity) {
            throw new AssertionError(String.format(
                "Expected exactly %d accepted with zero contention, got %d.", capacity, accepted));
        }

        // One more attempt beyond capacity must be rejected.
        boolean oneMore = limiter.add();
        if (oneMore) {
            throw new AssertionError("Accepted an event beyond capacity with zero contention.");
        }
    }

    // ================================================================================
    // TEST 2: EXACT BOUNDARY - LOW CONCURRENCY (2 AND 4 THREADS)
    // ================================================================================
    // Enough contention to exercise the CAS loop, low enough to reason about by hand
    // if it fails. Total attempts == capacity exactly, so the correct answer is
    // deterministic: accepted must equal capacity, no more, no less.
    @Test
    @DisplayName("Boundary: low concurrency (2 and 4 threads), attempts == capacity exactly")
    void testExactBoundaryLowConcurrency() throws Exception {
        int capacity = 10_000;
        long windowMs = 5_000;
        int[] threadCounts = {2, 4};

        for (int threads : threadCounts) {
            TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                    .nTimelines(1).build();
            limiter.start();

            int attemptsPerThread = capacity / threads;
            CyclicBarrier barrier = new CyclicBarrier(threads);
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            AtomicLong accepted = new AtomicLong(0);
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await();
                        for (int i = 0; i < attemptsPerThread; i++) {
                            if (limiter.add()) accepted.incrementAndGet();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
            executor.shutdown();

            System.out.printf("threads=%d: accepted=%,d expected=%,d%n", threads, accepted.get(), capacity);
            if (accepted.get() != capacity) {
                throw new AssertionError(String.format(
                    "At %d threads: expected exactly %,d accepted (attempts == capacity), got %,d.",
                    threads, capacity, accepted.get()));
            }
        }
    }

    // ================================================================================
    // TEST 3: canAdd()/add() CONSISTENCY - FIXED (attempts now exceed capacity)
    // ================================================================================
    @Test
    @DisplayName("canAdd()/add() consistency, attempts well past capacity this time")
    void testCanAddAddConsistencyPastCapacity() throws Exception {
        int capacity = 200_000;
        long windowMs = 5_000; // long window: avoid a mid-test reset confusing the count
        int threads = 32;
        int attemptsPerThread = 20_000; // threads * attemptsPerThread = 640,000, > 3x capacity

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(1).build();
        limiter.start();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicLong checkedYesThenAddNo = new AtomicLong(0);
        AtomicLong checkedNoThenAddYes = new AtomicLong(0);
        AtomicLong total = new AtomicLong(0);
        AtomicLong accepted = new AtomicLong(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                for (int i = 0; i < attemptsPerThread; i++) {
                    boolean could = limiter.canAdd();
                    boolean did = limiter.add();
                    total.incrementAndGet();
                    if (did) accepted.incrementAndGet();
                    if (could && !did) checkedYesThenAddNo.incrementAndGet();
                    if (!could && did) checkedNoThenAddYes.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.printf("Total accepted: %,d / capacity %,d (confirms capacity was actually reached: %s)%n",
                accepted.get(), capacity, accepted.get() >= capacity);
        double mismatchPct = 100.0 * (checkedYesThenAddNo.get() + checkedNoThenAddYes.get()) / total.get();
        System.out.printf("Mismatches: yes-then-no=%,d  no-then-yes=%,d  out of %,d pairs (%.3f%%)%n",
                checkedYesThenAddNo.get(), checkedNoThenAddYes.get(), total.get(), mismatchPct);

        if (accepted.get() < capacity) {
            throw new AssertionError("Test invalid: capacity was never fully reached, so this mismatch " +
                    "rate is meaningless. Increase attemptsPerThread.");
        }
        if (accepted.get() > capacity) {
            throw new AssertionError(String.format(
                "Accepted %,d exceeds capacity %,d - the CAS fix did not hold here.", accepted.get(), capacity));
        }
    }

    // ================================================================================
    // TEST 4: LATENCY NEAR SATURATION (last ~1% of capacity, where CAS retries concentrate)
    // ================================================================================
    // The existing latency test averages over an entire run. This isolates just the
    // calls made once the limiter is nearly full, which is where compareAndSet failures
    // (and therefore retry loops) are expected to cluster.
    @Test
    @DisplayName("Latency: add() calls specifically in the last 1% of capacity under contention")
    void testLatencyNearSaturation() throws Exception {
        int capacity = 1_000_000;
        long windowMs = 10_000; // long window so we don't get a reset mid-test
        int threads = 64;
        long nearSaturationThreshold = (long) (capacity * 0.99); // start measuring after this many accepted

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(1).build();
        limiter.start();

        AtomicLong accepted = new AtomicLong(0);
        List<Long> nearSaturationLatenciesNs = Collections.synchronizedList(new ArrayList<>());
        List<Long> earlyLatenciesNs = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        int attemptsPerThread = (capacity * 2) / threads; // guarantee we push well past capacity
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                for (int i = 0; i < attemptsPerThread; i++) {
                    boolean isNearSaturation = accepted.get() >= nearSaturationThreshold;
                    long t0 = System.nanoTime();
                    boolean ok = limiter.add();
                    long elapsed = System.nanoTime() - t0;
                    if (ok) accepted.incrementAndGet();

                    if (isNearSaturation) {
                        nearSaturationLatenciesNs.add(elapsed);
                    } else {
                        earlyLatenciesNs.add(elapsed);
                    }
                }
            }));
        }
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("Early-phase samples: " + earlyLatenciesNs.size());
        System.out.println("Near-saturation samples: " + nearSaturationLatenciesNs.size());
        printPercentiles("Early phase (low contention on capacity check)", earlyLatenciesNs);
        printPercentiles("Near saturation (>=99% full, high CAS contention expected)", nearSaturationLatenciesNs);
    }

    private static void printPercentiles(String label, List<Long> samplesList) {
        if (samplesList.isEmpty()) {
            System.out.println(label + ": no samples collected.");
            return;
        }
        long[] samples = samplesList.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(samples);
        int n = samples.length;
        System.out.printf("%s: P50=%dns P90=%dns P99=%dns Max=%dns%n",
                label, samples[(int) (n * 0.50)], samples[(int) (n * 0.90)],
                samples[(int) (n * 0.99)], samples[n - 1]);
    }

    // ================================================================================
    // TEST 5: FAIRNESS UNDER CAS CONTENTION NEAR THE BOUNDARY
    // ================================================================================
    // compareAndSet gives no fairness guarantee. Checks whether rejections near the
    // boundary concentrate on the same thread(s) repeatedly (real starvation) or
    // spread out roughly evenly (expected/acceptable).
    @Test
    @DisplayName("Fairness: rejections near capacity are not concentrated on the same thread")
    void testFairnessNearBoundary() throws Exception {
        int capacity = 50_000;
        long windowMs = 5_000;
        int threads = 16;
        int attemptsPerThread = 10_000; // threads * attemptsPerThread = 160,000, > 3x capacity

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(1).build();
        limiter.start();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        long[] rejectedByThread = new long[threads];
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int idx = t;
            futures.add(executor.submit(() -> {
                for (int i = 0; i < attemptsPerThread; i++) {
                    if (!limiter.add()) rejectedByThread[idx]++;
                }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("Rejected per thread: " + Arrays.toString(rejectedByThread));
        long min = Arrays.stream(rejectedByThread).min().orElse(0);
        long max = Arrays.stream(rejectedByThread).max().orElse(0);
        System.out.printf("Min rejected: %d, Max rejected: %d%n", min, max);
        System.out.println("(Informational - no hard assertion. A large min/max spread would suggest " +
                "some threads are systematically losing the CAS race more than others.)");
    }
}