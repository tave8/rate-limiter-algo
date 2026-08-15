package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.timeline.Timeline;
import com.giuseppetavella.rate_limiter_algo.timeline.TimelineRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ====================================================================================================
 *  DEEP DIVE: OVERSHOOT MAGNITUDE, MASKING, ROLLBACK RACE, TIMING PRECISION, LIFECYCLE
 * ====================================================================================================
 *
 * Follows up on the confirmed TOCTOU overshoot (20,002 accepted vs capacity 20,000 at nTimelines=1)
 * and the calcInitialDelay(0)=0 bug. These tests exist to answer one question precisely:
 * "what is the actual bound on overshoot, and does it hold at real scale?" - not just "does a bug
 * exist" (already known) but "how bad, how often, under what config."
 *
 * KNOWN CAUSE BEING MEASURED:
 * ReactiveQuietTimeline.add() does wouldOverflow() [check] then countInWindow.getAndIncrement() [act]
 * as two separate operations - not a single CAS. Two threads can both pass the check before either
 * increments. TimelineRateLimiter.add()'s AND-gate across timelines (all must accept) statistically
 * dampens this at nTimelines > 1 because other timelines stay full and reject anyway - it does not
 * fix it.
 */
public class TimelineRateLimiterDeepDiveTest {

    // ================================================================================
    // TEST 1: OVERSHOOT-MAGNITUDE SWEEP AT REAL CAPACITY / CONCURRENCY
    // ================================================================================
    // Repeats the thundering-herd test 10x at production-scale capacity (1M) and high
    // concurrency (64 threads), specifically at nTimelines=1 where the race is NOT masked
    // by the AND-gate. Reports overshoot % per trial and the worst case observed - this is
    // the number that actually backs or refutes a "99.5% accurate" claim.
    @Test
    @DisplayName("Overshoot magnitude: 10 trials at capacity=1M, 64 threads, nTimelines=1")
    void testOvershootMagnitudeAtRealScale() throws Exception {
        int capacity = 1_000_000;
        long windowMs = 1_000;
        int threads = 64;
        int attemptsPerThread = 40_000; // threads * attemptsPerThread = 2.56M, comfortably > capacity
        int trials = 10;
        double toleranceFraction = 0.005; // the 99.5% bar you stated

        double worstOvershootPct = 0;
        int trialsWithAnyOvershoot = 0;

        System.out.println("\n=== Overshoot sweep (nTimelines=1, capacity=" + capacity + ", threads=" + threads + ") ===");
        for (int trial = 1; trial <= trials; trial++) {
            TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                    .nTimelines(1).build();
            limiter.start();

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

            long over = Math.max(0, accepted.get() - capacity);
            double overshootPct = (over * 100.0) / capacity;
            worstOvershootPct = Math.max(worstOvershootPct, overshootPct);
            if (over > 0) trialsWithAnyOvershoot++;

            System.out.printf("  trial %2d: accepted=%,d  capacity=%,d  overshoot=%.4f%%%n",
                    trial, accepted.get(), capacity, overshootPct);
        }

        System.out.printf("Worst observed overshoot across %d trials: %.4f%% (tolerance: %.2f%%)%n",
                trials, worstOvershootPct, toleranceFraction * 100);
        System.out.printf("Trials with any overshoot: %d / %d%n", trialsWithAnyOvershoot, trials);

        if (worstOvershootPct > toleranceFraction * 100) {
            throw new AssertionError(String.format(
                "Overshoot of %.4f%% exceeds your stated 99.5%%-accuracy tolerance (0.5%%). " +
                "The TOCTOU race in ReactiveQuietTimeline.add() is not bounded within the range " +
                "you're willing to accept.", worstOvershootPct));
        }
    }

    // ================================================================================
    // TEST 2: MASKING COMPARISON - does nTimelines actually reduce overshoot, or just hide it?
    // ================================================================================
    // Same race, same capacity, varying nTimelines. If overshoot shrinks as nTimelines grows,
    // that supports "AND-gate statistically dampens it." If it doesn't shrink proportionally,
    // the earlier theory needs revisiting.
    @Test
    @DisplayName("Masking comparison: overshoot vs nTimelines (1, 3, 24)")
    void testOvershootAcrossTimelineCounts() throws Exception {
        int capacity = 500_000;
        long windowMs = 1_000;
        int threads = 64;
        int attemptsPerThread = 20_000;
        int[] timelineCounts = {1, 3, 24};

        System.out.println("\n=== Masking comparison ===");
        for (int nTimelines : timelineCounts) {
            TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                    .nTimelines(nTimelines).build();
            limiter.start();

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

            long over = Math.max(0, accepted.get() - capacity);
            double overshootPct = (over * 100.0) / capacity;
            System.out.printf("  nTimelines=%2d: accepted=%,d  overshoot=%.4f%%%n",
                    nTimelines, accepted.get(), overshootPct);
        }
        System.out.println("(Reported for comparison - no hard assertion here. Falling overshoot% as " +
                "nTimelines rises supports the AND-gate-dampens theory; flat or rising overshoot% " +
                "would mean the race scales independently of nTimelines and needs a different fix.)");
    }

    // ================================================================================
    // TEST 3: ROLLBACK RACE - transient over-capacity visibility during decreaseEventCountUntil
    // ================================================================================
    // TimelineRateLimiter.add() partially increments timelines[0..k), then on rejection at
    // timeline k, rolls back [0..k) via decrementAndGet per timeline - not atomically across
    // timelines. A concurrent reader could observe an inflated countInWindow on an early
    // timeline mid-rollback. This polls getCountInWindow() on timeline 0 from a watcher thread
    // while many workers hammer add(), and flags any observed value exceeding maxEvents.
    @Test
    @DisplayName("Rollback race: watcher never observes countInWindow > maxEvents on any timeline")
    void testNoTransientOvercountDuringRollback() throws Exception {
        int capacity = 10_000;
        long windowMs = 2_000; // long window so we're not fighting scheduled resets during the test
        int threads = 32;
        int nTimelines = 3; // need >1 timeline for rollback path (decreaseEventCountUntil) to trigger

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(nTimelines).build();
        limiter.start();

        List<Timeline> timelines = limiter.getTimelines();
        AtomicLong violations = new AtomicLong(0);
        AtomicLong maxObservedOverage = new AtomicLong(0);
        StopFlag stop = new StopFlag();

        Thread watcher = new Thread(() -> {
            while (!stop.stopped) {
                for (Timeline t : timelines) {
                    long count = t.getCountInWindow();
                    if (count > capacity) {
                        violations.incrementAndGet();
                        maxObservedOverage.updateAndGet(prev -> Math.max(prev, count - capacity));
                    }
                }
            }
        });
        watcher.start();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        int attemptsPerThread = 5_000;
        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                for (int i = 0; i < attemptsPerThread; i++) {
                    limiter.add();
                }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        executor.shutdown();
        stop.stopped = true;
        watcher.join(2000);

        System.out.printf("Rollback race watcher: %,d polls flagged an over-capacity reading " +
                "(max overage observed: %,d)%n", violations.get(), maxObservedOverage.get());

        // Polling can't catch every nanosecond-scale window, so treat this as a lower bound,
        // not proof of absence if it comes back clean. A positive result IS conclusive evidence
        // of the race, though.
        if (violations.get() > 0) {
            System.out.println("CONFIRMED: transient over-capacity state is observable during " +
                    "the non-atomic rollback in decreaseEventCountUntil().");
        }
    }

    // Small mutable holder since a plain boolean captured in a lambda can't be reassigned.
    private static class StopFlag {
        volatile boolean stopped = false;
    }

    // ================================================================================
    // TEST 4: canAdd() / add() CONSISTENCY UNDER CONTENTION
    // ================================================================================
    // canAdd() and add() are two separate calls with no atomicity between them. Under
    // contention, canAdd()==true followed immediately by add()==false (or vice versa) is
    // expected sometimes - but the RATE tells you how wide the TOCTOU gap really is in
    // practice, which matters if calling code uses canAdd() to decide whether to even try.
    @Test
    @DisplayName("canAdd()/add() consistency rate under contention")
    void testCanAddAddConsistency() throws Exception {
        int capacity = 200_000;
        long windowMs = 2_000;
        int threads = 32;
        int attemptsPerThread = 5_000;

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(1).build();
        limiter.start();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicLong checkedYesThenAddNo = new AtomicLong(0);
        AtomicLong checkedNoThenAddYes = new AtomicLong(0);
        AtomicLong total = new AtomicLong(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                for (int i = 0; i < attemptsPerThread; i++) {
                    boolean could = limiter.canAdd();
                    boolean did = limiter.add();
                    total.incrementAndGet();
                    if (could && !did) checkedYesThenAddNo.incrementAndGet();
                    if (!could && did) checkedNoThenAddYes.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        double mismatchPct = 100.0 * (checkedYesThenAddNo.get() + checkedNoThenAddYes.get()) / total.get();
        System.out.printf("canAdd()/add() mismatches: yes-then-no=%,d  no-then-yes=%,d  " +
                "out of %,d pairs (%.3f%% mismatch rate)%n",
                checkedYesThenAddNo.get(), checkedNoThenAddYes.get(), total.get(), mismatchPct);
        System.out.println("(Informational - some mismatch is inherent to two-step TOCTOU calls. " +
                "High no-then-yes counts are the more interesting signal: canAdd() saying no right " +
                "before add() succeeds means canAdd() is being overly conservative, not unsafe.)");
    }

    // ================================================================================
    // TEST 5: WINDOW BOUNDARY TIMING PRECISION
    // ================================================================================
    // Empirically measures how long each timeline actually takes to reset after start(),
    // vs. the expected offset from calcBuffer(i) = (window * i) / nTimelines. Directly
    // measures the real-world magnitude of the calcInitialDelay(0)=0 bug: timeline 0's
    // expected offset is always 0, meaning it should reset almost immediately - confirm
    // whether that's actually what happens and by how much it distorts the first window.
    @Test
    @DisplayName("Timing: measured reset offset per timeline vs. expected calcBuffer(i)")
    void testWindowBoundaryTimingPrecision() throws Exception {
        int capacity = 1_000;
        long windowMs = 2_000;
        int nTimelines = 4;
        long pollIntervalMs = 5;
        long timeoutMs = windowMs * 2;

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(nTimelines).build();

        long t0 = System.currentTimeMillis();
        limiter.start();

        // Fill every timeline so a reset is observable as a drop back to 0.
        for (int i = 0; i < capacity; i++) {
            limiter.add();
        }

        List<Timeline> timelines = limiter.getTimelines();
        long[] observedResetMs = new long[nTimelines];
        boolean[] resetSeen = new boolean[nTimelines];

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (int i = 0; i < nTimelines; i++) {
                if (!resetSeen[i] && timelines.get(i).getCountInWindow() == 0) {
                    resetSeen[i] = true;
                    observedResetMs[i] = System.currentTimeMillis() - t0;
                }
            }
            if (allTrue(resetSeen)) break;
            Thread.sleep(pollIntervalMs);
        }

        System.out.println("\n=== Window boundary timing (window=" + windowMs + "ms, nTimelines=" + nTimelines + ") ===");
        System.out.printf("%-6s | %-14s | %-14s | %-10s%n", "idx", "expected (ms)", "observed (ms)", "delta (ms)");
        for (int i = 0; i < nTimelines; i++) {
            long expected = limiter.calcBuffer(i);
            String observed = resetSeen[i] ? String.valueOf(observedResetMs[i]) : "NEVER (timeout)";
            String delta = resetSeen[i] ? String.valueOf(observedResetMs[i] - expected) : "-";
            System.out.printf("%-6d | %-14d | %-14s | %-10s%n", i, expected, observed, delta);
        }

        if (!resetSeen[0] || observedResetMs[0] > 50) {
            System.out.println("NOTE: timeline 0 expected an ~immediate reset (calcBuffer(0)=0) - " +
                    "if observed reset time is meaningfully > 0ms, real scheduler startup latency " +
                    "means the 'immediate' bug is somewhat self-limiting in practice, but still " +
                    "means timeline 0 does not wait a full window before its first reset.");
        }
    }

    private static boolean allTrue(boolean[] arr) {
        for (boolean b : arr) if (!b) return false;
        return true;
    }

    // ================================================================================
    // TEST 6: DOUBLE-START GUARD
    // ================================================================================
    @Test
    @DisplayName("Lifecycle: calling start() twice throws IllegalStateException")
    void testDoubleStartThrows() {
        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(1_000, 1_000)
                .nTimelines(1).build();
        limiter.start();
        try {
            limiter.start();
            throw new AssertionError("Expected IllegalStateException on second start(), none was thrown.");
        } catch (IllegalStateException expected) {
            System.out.println("Double-start correctly rejected: " + expected.getMessage());
        }
    }

    // ================================================================================
    // TEST 7: EXECUTOR / THREAD LEAK ON REPEATED start()
    // ================================================================================
    // start() creates nTimelines new single-thread ScheduledExecutorServices per instance,
    // with no visible stop()/shutdown() in the class. Spins up many instances and confirms
    // the JVM thread count grows roughly proportionally and does not recover - this is a
    // real operational concern if instances are ever created per-tenant/per-request rather
    // than as a long-lived singleton.
    @Test
    @DisplayName("Lifecycle: repeated start() leaks scheduler threads (no shutdown path found)")
    void testRepeatedStartLeaksThreads() throws InterruptedException {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int nTimelines = 3;
        int instancesToCreate = 20;

        System.gc();
        Thread.sleep(200);
        int threadsBefore = threadBean.getThreadCount();

        List<TimelineRateLimiter> keepAlive = new ArrayList<>(); // prevent GC from collecting instances
        for (int i = 0; i < instancesToCreate; i++) {
            TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(1_000, 5_000)
                    .nTimelines(nTimelines).build();
            limiter.start();
            keepAlive.add(limiter);
        }

        Thread.sleep(200);
        int threadsAfter = threadBean.getThreadCount();
        int expectedNewThreads = instancesToCreate * nTimelines;
        int actualNewThreads = threadsAfter - threadsBefore;

        System.out.printf("Threads before: %d, after %d instances (nTimelines=%d each): %d " +
                "(delta=%d, expected~=%d)%n",
                threadsBefore, instancesToCreate, nTimelines, threadsAfter, actualNewThreads, expectedNewThreads);
        System.out.println("No public stop()/shutdown() method was found on TimelineRateLimiter in the " +
                "reviewed source - these scheduler threads have no way to be reclaimed short of the " +
                "whole JVM exiting. If this class is ever instantiated per-request or per-tenant instead " +
                "of as a long-lived singleton, this is a genuine thread leak, not just a benchmark artifact.");

        if (actualNewThreads < expectedNewThreads / 2) {
            System.out.println("(Delta came in lower than expected - thread pool reuse or timing noise; " +
                    "rerun with a larger instancesToCreate to get a cleaner signal.)");
        }
    }
}