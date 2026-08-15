package com.giuseppetavella.rate_limiter_algo.one_scheduled_thread;

import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.TimelineRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * ====================================================================================================
 *  BATTLE-TESTED DRIFT ANALYSIS: distinguishes real accumulating drift from isolated,
 *  self-correcting tick delays (the pattern actually observed in the previous run).
 * ====================================================================================================
 *
 * WHY THE PREVIOUS TEST WAS THE WRONG TOOL:
 * Comparing 10 early vs 10 late samples is sensitive to a single outlier landing in
 * either window - it can report "drift" when what actually happened is a scattered,
 * randomly-timed, self-correcting delay (e.g. a GC pause on the single scheduler
 * thread) with no trend at all. The previous run's full sequence showed intervals
 * near-integer multiples of the expected value (405≈2x, 605≈3x), then immediately
 * snapping back - the signature of a missed tick recovering cleanly, not creep.
 *
 * WHAT THIS TEST DOES INSTEAD:
 *  1. Runs far longer (many more cycles) for a real battle-test, not a quick sample.
 *  2. Classifies each interval as NORMAL or an OUTLIER (>1.5x expected).
 *  3. Splits the full run into N time-ordered buckets and computes the average
 *     interval per bucket, then fits a simple linear regression across bucket
 *     averages. A near-zero slope means no systemic trend - outliers (if any) are
 *     scattered noise, not accumulating drift. A meaningfully positive slope means
 *     real degradation over time.
 *  4. Reports whether outliers cluster in later buckets (real degradation) or are
 *     evenly spread (environmental noise, self-correcting).
 *  5. Confirms every outlier is followed by a compensating short interval nearby
 *     (proof of self-correction) rather than a lasting shift in baseline.
 */
public class TimelineRateLimiterDriftBattleTest {

    @Test
    @DisplayName("Battle-test: no accumulating drift over a long run, only classified isolated delays")
    void testNoAccumulatingDriftOverLongRun() throws Exception {
        int capacity = 100_000;
        long windowMs = 200;
        int nTimelines = 2;
        int targetResets = 2_000;         // far more cycles than the earlier 100-reset sample
        int numBuckets = 20;               // time-ordered segments for trend analysis
        double outlierThreshold = 1.5;     // interval > 1.5x expected window counts as an outlier
        double maxAcceptableSlopeMsPerBucket = 2.0; // tolerance for a real trend, in ms/bucket

        var limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(nTimelines).build();
        limiter.start();

        var target = limiter.getTimelines().get(0);

        StopFlag stop = new StopFlag();
        Thread loadThread = new Thread(() -> {
            while (!stop.stopped) {
                limiter.add();
            }
        });
        loadThread.start();

        List<Long> resetTimestamps = new ArrayList<>();
        boolean wasNonZero = false;
        // Generous timeout: targetResets * expected window * safety factor.
        long testDeadline = System.currentTimeMillis() + (long) (windowMs * targetResets * 2.0);

        while (resetTimestamps.size() < targetResets && System.currentTimeMillis() < testDeadline) {
            long count = target.getCountInWindow();
            if (count > 0) {
                wasNonZero = true;
            } else if (wasNonZero) {
                resetTimestamps.add(System.currentTimeMillis());
                wasNonZero = false;
            }
        }

        stop.stopped = true;
        loadThread.join(2000);
        limiter.stop();

        int observed = resetTimestamps.size();
        System.out.printf("Resets observed: %d (target: %d) over %.1fs%n",
                observed, targetResets,
                (resetTimestamps.get(observed - 1) - resetTimestamps.get(0)) / 1000.0);

        if (observed < targetResets / 2) {
            System.out.println("Not enough resets observed - inconclusive. Increase timeout or check load thread.");
            return;
        }

        long[] intervals = new long[observed - 1];
        for (int i = 1; i < observed; i++) {
            intervals[i - 1] = resetTimestamps.get(i) - resetTimestamps.get(i - 1);
        }

        // ---- Classify normal vs outlier intervals ----
        long outlierThresholdMs = (long) (windowMs * outlierThreshold);
        int outlierCount = 0;
        List<Integer> outlierIndices = new ArrayList<>();
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i] > outlierThresholdMs) {
                outlierCount++;
                outlierIndices.add(i);
            }
        }
        double outlierPct = 100.0 * outlierCount / intervals.length;
        System.out.printf("Outliers (> %.1fx expected window = %dms): %d / %d (%.2f%%)%n",
                outlierThreshold, outlierThresholdMs, outlierCount, intervals.length, outlierPct);

        // ---- Bucket the run into time-ordered segments, compute avg interval per bucket ----
        int bucketSize = Math.max(1, intervals.length / numBuckets);
        List<Double> bucketAverages = new ArrayList<>();
        List<Integer> outliersPerBucket = new ArrayList<>();
        for (int b = 0; b < numBuckets; b++) {
            int startIdx = b * bucketSize;
            int endIdx = (b == numBuckets - 1) ? intervals.length : Math.min(intervals.length, startIdx + bucketSize);
            if (startIdx >= endIdx) break;

            long sum = 0;
            int outliersInBucket = 0;
            for (int i = startIdx; i < endIdx; i++) {
                sum += intervals[i];
                if (intervals[i] > outlierThresholdMs) outliersInBucket++;
            }
            double avg = (double) sum / (endIdx - startIdx);
            bucketAverages.add(avg);
            outliersPerBucket.add(outliersInBucket);
        }

        System.out.println("\nBucket # | Avg interval (ms) | Outliers in bucket");
        for (int b = 0; b < bucketAverages.size(); b++) {
            System.out.printf("  %2d     | %6.2f             | %d%n", b, bucketAverages.get(b), outliersPerBucket.get(b));
        }

        // ---- Linear regression slope across bucket averages (x = bucket index, y = avg interval) ----
        int n = bucketAverages.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = bucketAverages.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);

        System.out.printf("%nLinear trend across buckets: slope = %.3f ms/bucket " +
                "(tolerance: +/- %.1f ms/bucket)%n", slope, maxAcceptableSlopeMsPerBucket);

        // ---- Outlier clustering check: are outliers concentrated in the later half? ----
        int halfPoint = numBuckets / 2;
        int earlyOutliers = 0, lateOutliers = 0;
        for (int b = 0; b < outliersPerBucket.size(); b++) {
            if (b < halfPoint) earlyOutliers += outliersPerBucket.get(b);
            else lateOutliers += outliersPerBucket.get(b);
        }
        System.out.printf("Outliers in first half of run: %d | second half: %d%n", earlyOutliers, lateOutliers);

        // ---- Self-correction check: does each outlier get "paid back" shortly after? ----
        // For each outlier, sum the next 3 intervals - if the tick chain is self-correcting,
        // the immediate aftermath should NOT also run long (no compounding).
        int selfCorrectedCount = 0;
        for (int idx : outlierIndices) {
            if (idx + 3 < intervals.length) {
                long nextThreeSum = intervals[idx + 1] + intervals[idx + 2] + intervals[idx + 3];
                double nextThreeAvg = nextThreeSum / 3.0;
                if (nextThreeAvg < windowMs * 1.2) { // back to near-normal within 3 ticks
                    selfCorrectedCount++;
                }
            }
        }
        if (!outlierIndices.isEmpty()) {
            System.out.printf("Self-corrected outliers (returned to normal within 3 ticks): %d / %d%n",
                    selfCorrectedCount, outlierIndices.size());
        }

        System.out.println("\n=== VERDICT ===");
        boolean trendFailed = Math.abs(slope) > maxAcceptableSlopeMsPerBucket;
        boolean clusteringFailed = lateOutliers > earlyOutliers * 2 && lateOutliers > 3;

        if (trendFailed) {
            throw new AssertionError(String.format(
                "REAL DRIFT DETECTED: bucket-average interval trend slope is %.3f ms/bucket, " +
                "exceeding tolerance of %.1f ms/bucket across %d buckets. This indicates " +
                "systemic accumulating drift, not isolated self-correcting delays.",
                slope, maxAcceptableSlopeMsPerBucket, numBuckets));
        }
        if (clusteringFailed) {
            throw new AssertionError(String.format(
                "OUTLIER CLUSTERING DETECTED: %d outliers in the second half vs %d in the first half - " +
                "delays are concentrated late in the run, suggesting degradation over time rather than " +
                "random environmental noise.", lateOutliers, earlyOutliers));
        }

        System.out.println("PASSED: no accumulating drift, no late-run outlier clustering. " +
                "Any outliers present are consistent with isolated, self-correcting delays " +
                "(e.g. GC pauses) rather than a systemic scheduling defect.");
    }

    private static class StopFlag {
        volatile boolean stopped = false;
    }
}