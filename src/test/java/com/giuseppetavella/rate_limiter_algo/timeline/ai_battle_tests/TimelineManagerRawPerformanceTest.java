package com.giuseppetavella.rate_limiter_algo.timeline.ai_battle_tests;

import com.giuseppetavella.rate_limiter_algo.timeline.TimelineManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ====================================================================================================
 *                          RAW PERFORMANCE BENCHMARK — TIME / SPACE / ACCURACY
 * ====================================================================================================
 *
 * A corrected benchmark harness for the timeline-based rate limiter. It replaces the earlier battle
 * tests, which produced misleading numbers because the default {@code ReactiveTimeline#add()} THROWS
 * on rejection — killing the load-generator threads the instant a timeline filled up. See
 * BATTLE_TEST_RESULTS.md for the full root-cause writeup.
 *
 * Corrections applied here:
 *   - TIME     : capacity is sized so the accept path is NEVER rejected during the run (no throws,
 *                threads survive). Latency is measured per BATCH of calls, not per single call, to
 *                clear the System.nanoTime() resolution floor that produced the bogus "0 ns" readings.
 *   - SPACE    : the algorithm claims constant space (a fixed number of counters per timeline,
 *                independent of traffic). We drive tens of millions of events through a single manager
 *                and measure the heap delta to confirm memory does not grow with event volume.
 *   - ACCURACY : relies on the manager's default timeline (now the non-throwing ReactiveQuietTimeline,
 *                which returns false on rejection) so worker threads survive across
 *                window resets, and drives a busy-spin overload (no sub-millisecond Thread.sleep, which
 *                on Windows rounds up to ~15 ms and starves the generator).
 *
 * These are print-only benchmarks (no assertions); their value is the reported numbers.
 *
 * @author AI Battle-Test Suite (corrected harness)
 */
@Execution(ExecutionMode.SAME_THREAD)
public class TimelineManagerRawPerformanceTest {

    // ================================================================================================
    // TIME: throughput (ops/sec) and per-call latency percentiles across scaling thread pools.
    // ================================================================================================
    @Test
    @DisplayName("Raw Performance — TIME (throughput + batched latency, no-reject accept path)")
    void benchmarkTime() throws InterruptedException {
        // Capacity is effectively unbounded so add() always takes the accept path and never throws.
        int maxEvents = Integer.MAX_VALUE;
        long windowMs = 1_000;
        int nTimelines = 3;
        int testDurationSec = 2;
        int batchSize = 256; // amortizes nanoTime resolution across a batch of add() calls

        int[] threadCounts = {1, 4, 8, 16, 32, 64};

        System.out.println("\n==========================================================================================");
        System.out.println("                    RAW PERFORMANCE — TIME (THROUGHPUT & LATENCY)                         ");
        System.out.println("==========================================================================================");
        System.out.println("Config: unbounded capacity (accept path only), 3 timelines, 2s per thread count.");
        System.out.println("Latency = per-call ns derived from batches of " + batchSize + " add() calls.");
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.printf("%-9s | %-14s | %-16s | %-10s | %-10s | %-10s | %-10s%n",
                "Threads", "Total Ops", "Throughput", "P50 (ns)", "P90 (ns)", "P99 (ns)", "Max (ns)");
        System.out.println("------------------------------------------------------------------------------------------");

        for (int threads : threadCounts) {
            TimelineManager manager = new TimelineManager.Builder(maxEvents, windowMs, nTimelines).build();
            manager.start();

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            AtomicLong totalOps = new AtomicLong(0);
            List<long[]> perThreadSamples = Collections.synchronizedList(new ArrayList<>());

            long startTime = System.currentTimeMillis();
            long endTime = startTime + (testDurationSec * 1000L);

            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    long[] samples = new long[200_000];
                    int idx = 0;
                    long localOps = 0;
                    while (System.currentTimeMillis() < endTime) {
                        long startNs = System.nanoTime();
                        for (int b = 0; b < batchSize; b++) {
                            manager.add();
                        }
                        long perCall = (System.nanoTime() - startNs) / batchSize;
                        localOps += batchSize;
                        if (idx < samples.length) {
                            samples[idx++] = perCall;
                        }
                    }
                    totalOps.addAndGet(localOps);
                    perThreadSamples.add(Arrays.copyOf(samples, idx));
                });
            }

            executor.shutdown();
            executor.awaitTermination(testDurationSec + 2, TimeUnit.SECONDS);
            long actualMs = System.currentTimeMillis() - startTime;

            int total = perThreadSamples.stream().mapToInt(a -> a.length).sum();
            long[] all = new long[total];
            int offset = 0;
            for (long[] arr : perThreadSamples) {
                System.arraycopy(arr, 0, all, offset, arr.length);
                offset += arr.length;
            }
            Arrays.sort(all);

            long ops = totalOps.get();
            double throughput = ops / (actualMs / 1000.0);
            long p50 = total > 0 ? all[(int) (total * 0.50)] : 0;
            long p90 = total > 0 ? all[(int) (total * 0.90)] : 0;
            long p99 = total > 0 ? all[(int) (total * 0.99)] : 0;
            long max = total > 0 ? all[total - 1] : 0;

            System.out.printf(" %-8d | %-14d | %,12.0f ops/s | %-10d | %-10d | %-10d | %-10d%n",
                    threads, ops, throughput, p50, p90, p99, max);
        }
        System.out.println("==========================================================================================\n");
    }

    // ================================================================================================
    // SPACE: confirm memory footprint is constant w.r.t. event volume (constant-space claim).
    // ================================================================================================
    @Test
    @DisplayName("Raw Performance — SPACE (heap delta under tens of millions of events)")
    void benchmarkSpace() throws InterruptedException {
        int maxEvents = Integer.MAX_VALUE; // never reject → pure accept path, no exception objects
        long windowMs = 1_000;
        int nTimelines = 3;
        long[] eventVolumes = {1_000_000L, 10_000_000L, 50_000_000L};

        System.out.println("\n==========================================================================================");
        System.out.println("                         RAW PERFORMANCE — SPACE (CONSTANT-SPACE CHECK)                    ");
        System.out.println("==========================================================================================");
        System.out.println("Config: 3 timelines, unbounded capacity. Drives N events single-threaded, measures heap.");
        System.out.println("Expectation: heap delta stays flat regardless of event volume (space is O(timelines)).");
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.printf("%-16s | %-18s | %-18s | %-18s%n",
                "Events Driven", "Heap Before (KB)", "Heap After (KB)", "Delta (KB)");
        System.out.println("------------------------------------------------------------------------------------------");

        for (long volume : eventVolumes) {
            TimelineManager manager = new TimelineManager.Builder(maxEvents, windowMs, nTimelines).build();
            manager.start();

            long before = usedHeapKB();
            for (long i = 0; i < volume; i++) {
                manager.add();
            }
            long after = usedHeapKB();

            System.out.printf(" %-15d | %-18d | %-18d | %-18d%n",
                    volume, before, after, (after - before));
        }
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.println("Note: heap figures use Runtime after a GC hint; treat as indicative, not exact.");
        System.out.println("==========================================================================================\n");
    }

    private long usedHeapKB() throws InterruptedException {
        Runtime rt = Runtime.getRuntime();
        System.gc();
        Thread.sleep(150);
        System.gc();
        Thread.sleep(150);
        return (rt.totalMemory() - rt.freeMemory()) / 1024;
    }

    // ================================================================================================
    // ACCURACY: sustained overload, accepted vs ideal capacity, for N staggered timelines.
    // Relies on the manager's default timeline, now ReactiveQuietTimeline (returns false, no throw),
    // so worker threads survive across resets.
    // ================================================================================================
    @Test
    @DisplayName("Raw Performance — ACCURACY (sustained busy-spin overload, quiet timelines)")
    void benchmarkAccuracy() throws InterruptedException {
        int maxEventsPerWindow = 1_000;
        long windowMs = 1_000;
        int testDurationSec = 3;
        long idealAccepted = (long) maxEventsPerWindow * testDurationSec; // one quota per window

        int[] timelineCounts = {1, 2, 4, 8, 16};

        System.out.println("\n==========================================================================================");
        System.out.println("                       RAW PERFORMANCE — ACCURACY (SUSTAINED OVERLOAD)                     ");
        System.out.println("==========================================================================================");
        System.out.println("Config: 1000 events / 1000ms window, 3s, 8 busy-spinning threads, quiet (non-throwing) timelines.");
        System.out.println("Accuracy % = 100 - |accepted - ideal| / ideal * 100. Ideal = quota per window.");
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.printf("%-14s | %-12s | %-12s | %-12s | %-12s%n",
                "Timelines (N)", "Attempted", "Accepted", "Ideal", "Accuracy %");
        System.out.println("------------------------------------------------------------------------------------------");

        for (int nTimelines : timelineCounts) {
            TimelineManager manager = new TimelineManager.Builder(maxEventsPerWindow, windowMs, nTimelines).build();
            manager.start();

            AtomicLong attempted = new AtomicLong(0);
            AtomicLong accepted = new AtomicLong(0);

            int numThreads = 8;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            long endTime = System.currentTimeMillis() + (testDurationSec * 1000L);

            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    while (System.currentTimeMillis() < endTime) {
                        attempted.incrementAndGet();
                        if (manager.add()) {
                            accepted.incrementAndGet();
                        }
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(testDurationSec + 2, TimeUnit.SECONDS);

            long acc = accepted.get();
            double error = Math.abs(acc - idealAccepted) / (double) idealAccepted;
            double accuracyPct = Math.max(0, (1.0 - error) * 100.0);

            System.out.printf(" %-13d | %-12d | %-12d | %-12d | %10.2f%%%n",
                    nTimelines, attempted.get(), acc, idealAccepted, accuracyPct);
        }
        System.out.println("==========================================================================================\n");
    }

    // ================================================================================================
    // EXPERIMENT: isolate the calcBuffer integer-truncation effect on accuracy at high N.
    // A/B: window=1000 (1000/16 = 62.5 -> 62, truncated + uneven wrap) vs window=1024 (1024/16 = 64, exact).
    // Same maxEvents; ideal is scaled per config since ideal = maxEvents * duration / window.
    // ================================================================================================
    @Test
    @DisplayName("Experiment — stagger-offset truncation vs even tiling at N=16")
    void experimentStaggerTruncation() throws InterruptedException {
        int maxEvents = 1_000;
        int nTimelines = 16;
        int durationMs = 4_000;

        long[] windows = {1000, 1024}; // 1000/16 truncates; 1024/16 is exact

        System.out.println("\n==========================================================================================");
        System.out.println("            EXPERIMENT: STAGGER-OFFSET TRUNCATION (calcBuffer) AT N = 16                   ");
        System.out.println("==========================================================================================");
        for (long window : windows) {
            long step = window / nTimelines;                 // current formula's per-step offset (truncated)
            long lastOffsetCurrent = step * (nTimelines - 1); // (window/N)*factor  -> accumulates error
            long lastOffsetBetter = (window * (nTimelines - 1)) / nTimelines; // (window*factor)/N -> less error
            long wrapGap = window - step * nTimelines;        // slack folded into the last inter-reset gap
            System.out.printf("window=%d | step=(%d/%d)=%d ms | last offset: current=%d better=%d | wrap-gap slack=%d ms%n",
                    window, window, nTimelines, step, lastOffsetCurrent, lastOffsetBetter, wrapGap);
        }
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.printf("%-10s | %-12s | %-12s | %-12s | %-12s%n",
                "Window", "Attempted", "Accepted", "Ideal", "Accuracy %");
        System.out.println("------------------------------------------------------------------------------------------");

        for (long window : windows) {
            TimelineManager manager = new TimelineManager.Builder(maxEvents, window, nTimelines).build();
            System.out.printf("window=%d actual calcBuffer offsets: [1]=%d [8]=%d [15]=%d%n",
                    window, manager.calcBuffer(1), manager.calcBuffer(8), manager.calcBuffer(15));
            manager.start();

            AtomicLong attempted = new AtomicLong(0);
            AtomicLong accepted = new AtomicLong(0);

            int numThreads = 8;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            long endTime = System.currentTimeMillis() + durationMs;

            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    while (System.currentTimeMillis() < endTime) {
                        attempted.incrementAndGet();
                        if (manager.add()) {
                            accepted.incrementAndGet();
                        }
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(durationMs / 1000 + 2, TimeUnit.SECONDS);

            long ideal = Math.round((double) maxEvents * durationMs / window);
            long acc = accepted.get();
            double accuracyPct = Math.max(0, (1.0 - Math.abs(acc - ideal) / (double) ideal) * 100.0);
            System.out.printf(" %-9d | %-12d | %-12d | %-12d | %10.2f%%%n",
                    window, attempted.get(), acc, ideal, accuracyPct);
        }
        System.out.println("==========================================================================================\n");
    }
}