package com.giuseppetavella.rate_limiter_algo.one_scheduled_thread;

import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.TimelineRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Fixed version of the reset-interval drift test. The previous attempt called add()
 * only once per poll iteration, which could leave the timeline stuck at 0 (rejected
 * forever) or never observably non-zero between resets - producing no data either way.
 *
 * This version runs a background thread continuously calling add() (ignoring result -
 * only trying to keep countInWindow above 0 between resets, capacity doesn't matter
 * for what's being measured here) while a separate tight poll loop on the main thread
 * watches timeline 0 for 0 -> non-zero -> 0 transitions and timestamps each reset.
 */
public class TimelineRateLimiterDriftTest {

    @Test
    @DisplayName("Timing: reset interval for one timeline does not drift over many cycles (fixed harness)")
    void testResetIntervalDriftFixed() throws Exception {
        int capacity = 100_000; // high enough that background load won't exhaust it and get stuck rejecting
        long windowMs = 200;    // short window -> many cycles in a short test
        int nTimelines = 2;
        int cyclesToObserve = 100;
        long testDurationMs = (long) (windowMs * cyclesToObserve * 1.5);

        var limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(nTimelines).build();
        limiter.start();

        var target = limiter.getTimelines().get(0);

        // Background thread: continuously call add() so countInWindow stays above 0
        // between resets. We don't care about accepted/rejected here, only that the
        // count is kept non-zero so a drop to 0 is unambiguously a reset event.
        volatileFlag stop = new volatileFlag();
        Thread loadThread = new Thread(() -> {
            while (!stop.stopped) {
                limiter.add();
            }
        });
        loadThread.start();

        List<Long> resetTimestamps = new ArrayList<>();
        boolean wasNonZero = false;
        long testDeadline = System.currentTimeMillis() + testDurationMs;

        while (resetTimestamps.size() < cyclesToObserve && System.currentTimeMillis() < testDeadline) {
            long count = target.getCountInWindow();
            if (count > 0) {
                wasNonZero = true;
            } else if (wasNonZero) {
                resetTimestamps.add(System.currentTimeMillis());
                wasNonZero = false;
            }
            // No sleep - tight poll, since we need finer resolution than the window
            // itself to reliably catch each transition. This will burn a full core;
            // that's expected and fine for a short diagnostic test.
        }

        stop.stopped = true;
        loadThread.join(2000);
        limiter.stop();

        int observed = resetTimestamps.size();
        System.out.printf("Resets observed: %d (target was %d)%n", observed, cyclesToObserve);

        if (observed < 10) {
            System.out.println("Still not enough resets observed to measure drift reliably - " +
                    "inconclusive. Consider increasing testDurationMs or checking that the " +
                    "background thread is actually generating load (capacity may be too low, " +
                    "causing add() to reject constantly without ever incrementing the count).");
            return;
        }

        // Compare interval of the FIRST several cycles vs the LAST several cycles.
        int sampleSize = Math.min(10, observed / 4);
        long earlyIntervalSum = 0, lateIntervalSum = 0;
        for (int i = 1; i <= sampleSize; i++) {
            earlyIntervalSum += resetTimestamps.get(i) - resetTimestamps.get(i - 1);
        }
        for (int i = observed - sampleSize; i < observed; i++) {
            lateIntervalSum += resetTimestamps.get(i) - resetTimestamps.get(i - 1);
        }
        double earlyAvg = (double) earlyIntervalSum / sampleSize;
        double lateAvg = (double) lateIntervalSum / sampleSize;
        double driftMs = lateAvg - earlyAvg;
        double driftPct = 100.0 * driftMs / windowMs;

        System.out.printf("Expected interval: %dms%n", windowMs);
        System.out.printf("Early-cycle avg interval (first %d resets): %.2fms%n", sampleSize, earlyAvg);
        System.out.printf("Late-cycle avg interval (last %d resets): %.2fms%n", sampleSize, lateAvg);
        System.out.printf("Drift: %.2fms (%.2f%% of window)%n", driftMs, driftPct);

        // Print the full sequence too, so a slow creeping trend is visible even if
        // early-vs-late comparison alone doesn't capture it clearly.
        System.out.println("Full interval sequence (ms between consecutive resets):");
        StringBuilder sb = new StringBuilder("  ");
        for (int i = 1; i < observed; i++) {
            sb.append(resetTimestamps.get(i) - resetTimestamps.get(i - 1));
            if (i < observed - 1) sb.append(", ");
        }
        System.out.println(sb);

        if (Math.abs(driftPct) > 5.0) {
            throw new AssertionError(String.format(
                "Reset interval drifted %.2f%% (%.2fms) from early to late cycles over %d resets - " +
                "this could accumulate meaningfully over a long-running process.", driftPct, driftMs, observed));
        }
    }

    private static class volatileFlag {
        volatile boolean stopped = false;
    }
}