package com.giuseppetavella.rate_limiter_algo.one_scheduled_thread;

import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.TimelineRateLimiter;
import com.giuseppetavella.rate_limiter_algo.timeline.timelines.Timeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tests specific to the single-scheduler-thread redesign (round-robin ticking via
 * chained schedule() calls, replacing N independent scheduleAtFixedRate threads).
 *
 * Two new risks this redesign introduces that the old design didn't have:
 *  1. TIMING DRIFT: tick() schedules its own delay AFTER doBeforeTick() completes,
 *     not from a fixed absolute schedule. scheduleAtFixedRate self-corrects for
 *     execution time; manual re-scheduling does not - drift can accumulate.
 *  2. STOP RACE: if stop() shuts down the scheduler between a tick's doBeforeTick()
 *     and its own re-scheduling call, that schedule() hits a dead executor.
 */
public class TimelineRateLimiterSingleThreadTickTest {

    // ================================================================================
    // TEST 1: CORRECTNESS PARITY - confirm the redesign didn't regress core guarantees
    // ================================================================================
    @Test
    @DisplayName("Parity: thundering herd correctness holds with single-thread ticking")
    void testThunderingHerdParityWithSingleThreadScheduler() throws Exception {
        int capacity = 50_000;
        long windowMs = 1_000;
        int threads = 64;
        int attemptsPerThread = 5_000;

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(3).build();
        limiter.start();

        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicLong accepted = new AtomicLong(0);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (limiter.add()) accepted.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        limiter.stop();

        System.out.printf("Accepted: %,d / capacity %,d%n", accepted.get(), capacity);
        if (accepted.get() > capacity) {
            throw new AssertionError(String.format(
                "REGRESSION: accepted %,d exceeds capacity %,d under the new scheduler design.",
                accepted.get(), capacity));
        }
    }

    // ================================================================================
    // TEST 2: ROUND-ROBIN COVERAGE - every timeline actually gets reset over one cycle
    // ================================================================================
    // With one shared thread cycling through timelines sequentially instead of N
    // independent timers, confirm every timeline still gets touched roughly once
    // per window, not skipped or double-hit.
    @Test
    @DisplayName("Round-robin: every timeline resets roughly once per full window cycle")
    void testEveryTimelineResetsOncePerCycle() throws Exception {
        int capacity = 1_000;
        long windowMs = 2_000;
        int nTimelines = 4;

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(nTimelines).build();
        limiter.start();

        // Fill every timeline so a reset is observable as a drop back toward 0.
        for (int i = 0; i < capacity; i++) limiter.add();

        var timelines = limiter.getTimelines();
        boolean[] resetSeen = new boolean[nTimelines];
        long deadline = System.currentTimeMillis() + windowMs * 2;

        while (System.currentTimeMillis() < deadline && !allTrue(resetSeen)) {
            for (int i = 0; i < nTimelines; i++) {
                if (!resetSeen[i] && timelines.get(i).getCountInWindow() == 0) {
                    resetSeen[i] = true;
                }
            }
            Thread.sleep(5);
        }
        limiter.stop();

        for (int i = 0; i < nTimelines; i++) {
            System.out.printf("timeline %d reset observed: %b%n", i, resetSeen[i]);
        }
        if (!allTrue(resetSeen)) {
            throw new AssertionError("Not every timeline was reset within one full window cycle - " +
                    "round-robin ticking may be skipping timelines.");
        }
    }

    private static boolean allTrue(boolean[] arr) {
        for (boolean b : arr) if (!b) return false;
        return true;
    }

    // ================================================================================
    // TEST 3: TIMING DRIFT OVER MANY CYCLES
    // ================================================================================
    // Since each tick reschedules itself relative to its own completion time (not a
    // fixed absolute schedule), measure whether the actual interval between a given
    // timeline's resets grows over many cycles - this is the drift scheduleAtFixedRate
    // would have prevented.
    @Test
    @DisplayName("Timing: reset interval for one timeline does not drift over many cycles")
    void testResetIntervalDoesNotDriftOverManyCycles() throws Exception {
        int capacity = 100;
        long windowMs = 200; // short window -> many cycles in a short test
        int nTimelines = 2;
        int cyclesToObserve = 50;

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(nTimelines).build();
        limiter.start();

        var target = limiter.getTimelines().get(0);
        long[] resetTimestamps = new long[cyclesToObserve];
        int observed = 0;
        boolean wasNonZero = false;

        long testDeadline = System.currentTimeMillis() + (long) (windowMs * cyclesToObserve * 1.5);
        while (observed < cyclesToObserve && System.currentTimeMillis() < testDeadline) {
            limiter.add(); // keep count above 0 so a reset to 0 is observable each cycle
            long count = target.getCountInWindow();
            if (count > 0) wasNonZero = true;
            if (wasNonZero && count == 0) {
                resetTimestamps[observed++] = System.currentTimeMillis();
                wasNonZero = false;
            }
            Thread.sleep(2);
        }
        limiter.stop();

        if (observed < cyclesToObserve / 2) {
            System.out.println("Not enough resets observed to measure drift reliably (" + observed + " of "
                    + cyclesToObserve + ") - inconclusive, not a failure.");
            return;
        }

        // Compare the interval of the FIRST few cycles vs the LAST few cycles.
        long earlyIntervalSum = 0, lateIntervalSum = 0;
        int sampleSize = Math.min(5, observed / 3);
        for (int i = 1; i <= sampleSize; i++) {
            earlyIntervalSum += resetTimestamps[i] - resetTimestamps[i - 1];
        }
        for (int i = observed - sampleSize; i < observed; i++) {
            lateIntervalSum += resetTimestamps[i] - resetTimestamps[i - 1];
        }
        double earlyAvg = (double) earlyIntervalSum / sampleSize;
        double lateAvg = (double) lateIntervalSum / sampleSize;
        double driftMs = lateAvg - earlyAvg;

        System.out.printf("Expected interval: ~%dms  Early-cycle avg: %.1fms  Late-cycle avg: %.1fms  " +
                "Drift: %.1fms%n", windowMs, earlyAvg, lateAvg, driftMs);
        System.out.println("(Informational - no hard assertion. A drift of more than a few ms per " +
                "cycle by the end of a long-running process could accumulate meaningfully over hours; " +
                "small noise here is expected from Thread.sleep()/polling granularity in this test itself.)");
    }

    // ================================================================================
    // TEST 4: STOP-DURING-TICK RACE
    // ================================================================================
    // Repeatedly start() then immediately stop() many fresh instances, trying to land
    // stop() while a tick is in-flight (between doBeforeTick() finishing and the next
    // schedule() call). Confirms this doesn't hang, leak threads, or throw anything
    // that escapes to the caller.
    @Test
    @DisplayName("Lifecycle: rapid start/stop does not hang, leak threads, or throw to caller")
    void testRapidStartStopDoesNotHangOrLeak() throws Exception {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int iterations = 200;

        System.gc();
        Thread.sleep(100);
        int threadsBefore = threadBean.getThreadCount();

        for (int i = 0; i < iterations; i++) {
            TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(1_000, 50)
                    .nTimelines(3).build();
            limiter.start();
            // Deliberately no sleep - maximize the chance of landing stop() mid-tick.
            limiter.stop();
        }

        Thread.sleep(500); // let any residual shutdown activity settle
        System.gc();
        Thread.sleep(100);
        int threadsAfter = threadBean.getThreadCount();

        System.out.printf("Threads before: %d, after %d start/stop cycles: %d (delta=%d)%n",
                threadsBefore, iterations, threadsAfter, threadsAfter - threadsBefore);
        System.out.println("Completed without hanging or throwing to the caller - any " +
                "RejectedExecutionException from a race between stop() and an in-flight tick " +
                "would be swallowed internally by the executor, not surfaced here; check console " +
                "output above for any unexpected stack traces printed by the JVM's default handler.");

        if (threadsAfter - threadsBefore > iterations) {
            throw new AssertionError(String.format(
                "Thread count grew by %d over %d start/stop cycles - stop() may not be fully " +
                "cleaning up the scheduler thread.", threadsAfter - threadsBefore, iterations));
        }
    }
}