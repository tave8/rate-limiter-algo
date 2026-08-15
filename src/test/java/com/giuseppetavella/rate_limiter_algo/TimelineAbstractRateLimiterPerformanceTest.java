package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.TimelineNThreadsRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
 *                                  BATTLE TEST 2: HIGH-CONCURRENCY THROUGHPUT & LATENCY
 * ====================================================================================================
 *
 * PURPOSE OF THIS TEST:
 * ---------------------
 * Battle-tests raw QPS (Queries Per Second) throughput and execution latency percentiles (P50, P90, P99, Max)
 * under heavy thread contention (1 to 64 parallel worker threads).
 *
 * HOW THE TEST WORKS:
 * -------------------
 * 1. Instantiates TimelineManager with high capacity (1,000,000 requests/sec).
 * 2. Fires requests continuously across scaling thread pools (1, 4, 8, 16, 32, 64 threads).
 * 3. Measures nanoTime duration for each add() execution to compute exact percentile latencies.
 *
 * PRACTICAL MEANING OF RESULTS:
 * -----------------------------
 * - Throughput (Ops/sec): Demonstrates the maximum query rate the algorithm can sustain per second.
 * - Latency (ns): Measures individual method call overhead to ensure the rate limiter does not slow down caller threads.
 * - Scalability Curve: Identifies CPU lock-free scalability and cache-line contention boundaries.
 *
 * @author AI Battle-Test Suite
 */
public class TimelineAbstractRateLimiterPerformanceTest {

    @Test
    @DisplayName("Battle-Test: High-Concurrency Throughput and Latency Percentiles (1 to 64 Threads)")
    void testThroughputAndLatencyPercentiles() throws InterruptedException {
        int maxEvents = 1_000_000;
        long windowMs = 1_000;
        int nTimelines = 3;
        int testDurationSec = 2;

        int[] threadCounts = {1, 4, 8, 16, 32, 64};

        System.out.println("\n==========================================================================================");
        System.out.println("            BATTLE-TEST 2: HIGH-CONCURRENCY THROUGHPUT & LATENCY PERCENTILES              ");
        System.out.println("==========================================================================================");
        System.out.println("Scenario: Driving maximum throughput across scaling thread pools (1 to 64 threads).");
        System.out.println("Goal: Benchmark QPS ceiling and microsecond call overhead on your exact code.");
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.printf("%-10s | %-12s | %-12s | %-10s | %-10s | %-10s | %-10s%n",
                "Threads", "Total Ops", "Throughput", "P50 (ns)", "P90 (ns)", "P99 (ns)", "Max (ns)");
        System.out.println("------------------------------------------------------------------------------------------");

        for (int threads : threadCounts) {
            TimelineNThreadsRateLimiter manager = new TimelineNThreadsRateLimiter.Builder(maxEvents, windowMs).nTimelines(nTimelines).build();
            manager.start();

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            AtomicLong totalOps = new AtomicLong(0);

            List<long[]> perThreadSamples = Collections.synchronizedList(new ArrayList<>());

            long startTime = System.currentTimeMillis();
            long endTime = startTime + (testDurationSec * 1000L);

            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    long[] samples = new long[100_000];
                    int sampleIdx = 0;

                    while (System.currentTimeMillis() < endTime) {
                        long startNs = System.nanoTime();
                        manager.add();
                        long elapsedNs = System.nanoTime() - startNs;

                        totalOps.incrementAndGet();

                        if (sampleIdx < samples.length) {
                            samples[sampleIdx++] = elapsedNs;
                        }
                    }

                    perThreadSamples.add(Arrays.copyOf(samples, sampleIdx));
                });
            }

            executor.shutdown();
            executor.awaitTermination(testDurationSec + 2, TimeUnit.SECONDS);
            long actualDurationMs = System.currentTimeMillis() - startTime;

            int totalCollected = perThreadSamples.stream().mapToInt(a -> a.length).sum();
            long[] allLatency = new long[totalCollected];
            int offset = 0;
            for (long[] arr : perThreadSamples) {
                System.arraycopy(arr, 0, allLatency, offset, arr.length);
                offset += arr.length;
            }

            Arrays.sort(allLatency);

            long ops = totalOps.get();
            double throughput = (ops / (actualDurationMs / 1000.0));

            long p50 = totalCollected > 0 ? allLatency[(int) (totalCollected * 0.50)] : 0;
            long p90 = totalCollected > 0 ? allLatency[(int) (totalCollected * 0.90)] : 0;
            long p99 = totalCollected > 0 ? allLatency[(int) (totalCollected * 0.99)] : 0;
            long max = totalCollected > 0 ? allLatency[totalCollected - 1] : 0;

            System.out.printf(" %-9d | %-12d | %-9.0f ops/s | %-10d | %-10d | %-10d | %-10d%n",
                    threads, ops, throughput, p50, p90, p99, max);
        }

        System.out.println("==========================================================================================\n");
    }
}
