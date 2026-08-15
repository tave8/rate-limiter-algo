package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.timeline.TimelineRateLimiter;
import com.giuseppetavella.rate_limiter_algo.timeline.Timelines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

public class TimelineAlgorithmStressTest {

    private static final int MAX_EVENTS = 10_000;
    private static final int WINDOW_MS = 1_000;
    private static final int TIMELINES_COUNT = 6;

    private TimelineRateLimiter manager;
    private ExecutorService executor;
    private int cores;

    @BeforeEach
    void setUp() {
        cores = Runtime.getRuntime().availableProcessors();
        executor = Executors.newFixedThreadPool(cores);

        manager = new TimelineRateLimiter.Builder(MAX_EVENTS, WINDOW_MS).nTimelines(TIMELINES_COUNT)
                .eventFilterer((t) -> true)
                .build();

        manager.setTimelineSupplier(() -> Timelines.newReactiveQuietBackoffFrom(manager));
        manager.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("1. Sustained Load Accuracy: Enforces strict capacity limits over time")
    void testSustainedLoadAccuracy() throws InterruptedException {
        int durationSeconds = 3;
        // Total capacity for N timelines over D seconds
        long totalCapacityCeiling = (long) MAX_EVENTS * TIMELINES_COUNT * durationSeconds;

        LongAdder totalAttempted = new LongAdder();
        LongAdder totalAccepted = new LongAdder();
        LongAdder totalRejected = new LongAdder();

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger activeThreads = new AtomicInteger(cores);

        long startTimeNanos = System.nanoTime();

        for (int i = 0; i < cores; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    long endTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSeconds);
                    while (System.nanoTime() < endTime) {
                        totalAttempted.increment();
                        if (manager.add()) {
                            totalAccepted.increment();
                        } else {
                            totalRejected.increment();
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    activeThreads.decrementAndGet();
                }
            });
        }

        startGate.countDown();

        while (activeThreads.get() > 0) {
            Thread.sleep(50);
        }

        double elapsedSeconds = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0;
        long accepted = totalAccepted.sum();
        long attempted = totalAttempted.sum();
        double accuracyRatio = (double) accepted / totalCapacityCeiling;

        printSectionHeader("SUSTAINED LOAD ACCURACY REPORT");
        System.out.printf("Execution Time         : %.2f seconds%n", elapsedSeconds);
        System.out.printf("Total Requests Sent    : %,d%n", attempted);
        System.out.printf("Capacity Ceiling       : %,d events%n", totalCapacityCeiling);
        System.out.printf("Actual Accepted        : %,d events%n", accepted);
        System.out.printf("Admissions Ratio       : %.4f (Target: <= 1.0000)%n", accuracyRatio);
        System.out.printf("Throughput             : %,.2f req/sec%n", attempted / elapsedSeconds);

        assertTrue(accepted <= totalCapacityCeiling, 
                "BURST LEAK DETECTED: Admitted more requests than maximum capacity ceiling!");
        assertTrue(accepted > 0, "No requests were accepted.");
    }

    @Test
    @DisplayName("2. Instantaneous Burst Protection: Clamps 10x traffic spike instantly")
    void testInstantaneousBurstProtection() throws InterruptedException {
        int singleWindowCapacity = MAX_EVENTS * TIMELINES_COUNT;
        int burstSize = singleWindowCapacity * 10; // 10x burst

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger acceptedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        CountDownLatch doneGate = new CountDownLatch(burstSize);

        for (int i = 0; i < burstSize; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    if (manager.add()) {
                        acceptedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown(); // Blast all threads simultaneously
        doneGate.await(5, TimeUnit.SECONDS);

        int accepted = acceptedCount.get();
        int rejected = rejectedCount.get();

        printSectionHeader("BURST PROTECTION REPORT");
        System.out.printf("Burst Volume Sent      : %,d requests%n", burstSize);
        System.out.printf("Single Window Capacity : %,d requests%n", singleWindowCapacity);
        System.out.printf("Burst Accepted         : %,d requests%n", accepted);
        System.out.printf("Burst Shed (Rejected)  : %,d requests%n", rejected);

        assertTrue(accepted <= singleWindowCapacity, 
                "BURST LEAK: Accepted more than single window capacity during burst!");
        assertEquals(burstSize, accepted + rejected, 
                "Accounting mismatch between accepted and rejected burst requests.");
    }

    @Test
    @DisplayName("3. Latency Percentiles: Measures nanosecond execution profiles under saturation")
    void testNanoLatencyProfile() {
        int sampleSize = 500_000;
        long[] latenciesNanos = new long[sampleSize];

        // Warmup cycle
        for (int i = 0; i < 10_000; i++) {
            manager.add();
        }

        // Sampling loop
        for (int i = 0; i < sampleSize; i++) {
            long start = System.nanoTime();
            manager.add();
            latenciesNanos[i] = System.nanoTime() - start;
        }

        Arrays.sort(latenciesNanos);

        long p50 = latenciesNanos[(int) (sampleSize * 0.50)];
        long p90 = latenciesNanos[(int) (sampleSize * 0.90)];
        long p99 = latenciesNanos[(int) (sampleSize * 0.99)];
        long p999 = latenciesNanos[(int) (sampleSize * 0.999)];
        long max = latenciesNanos[sampleSize - 1];

        printSectionHeader("SATURATED LATENCY PROFILE (NANOSECONDS)");
        System.out.printf("Sample Size   : %,d ops%n", sampleSize);
        System.out.printf("p50 Latency   : %,d ns (%.3f µs)%n", p50, p50 / 1_000.0);
        System.out.printf("p90 Latency   : %,d ns (%.3f µs)%n", p90, p90 / 1_000.0);
        System.out.printf("p99 Latency   : %,d ns (%.3f µs)%n", p99, p99 / 1_000.0);
        System.out.printf("p99.9 Latency : %,d ns (%.3f µs)%n", p999, p999 / 1_000.0);
        System.out.printf("Max Latency   : %,d ns (%.3f µs)%n", max, max / 1_000.0);

        assertTrue(p50 < 1_000, "p50 latency exceeded 1 microsecond!");
    }

    @Test
    @DisplayName("4. Recovery Precision: Measures window re-opening latency after exhaustion")
    void testRecoveryPrecision() throws InterruptedException {
        // 1. Fully exhaust current window capacity
        while (manager.add()) {
            // Spin until fully rejected
        }

        // Verify exhaustion state
        assertFalse(manager.add(), "Limiter should be fully exhausted.");

        // 2. Sleep past window expiration time
        Thread.sleep(WINDOW_MS + 20);

        // 3. Measure time to re-open
        long startNanos = System.nanoTime();
        boolean recovered = false;
        long spinAttempts = 0;

        while ((System.nanoTime() - startNanos) < TimeUnit.MILLISECONDS.toNanos(500)) {
            spinAttempts++;
            if (manager.add()) {
                recovered = true;
                break;
            }
        }

        long recoveryLagNanos = System.nanoTime() - startNanos;

        printSectionHeader("RECOVERY PRECISION REPORT");
        System.out.printf("Recovery Achieved      : %s%n", recovered ? "YES" : "NO");
        System.out.printf("Spin Attempts to Open  : %,d%n", spinAttempts);
        System.out.printf("Recovery Residual Lag  : %,d ns (%.3f ms)%n", 
                recoveryLagNanos, recoveryLagNanos / 1_000_000.0);

        assertTrue(recovered, "Limiter failed to re-open traffic after window expired!");
        assertTrue(recoveryLagNanos < TimeUnit.MILLISECONDS.toNanos(50), 
                "Recovery residual lag exceeded 50ms!");
    }

    private void printSectionHeader(String title) {
        System.out.println("\n==================================================");
        System.out.printf("   %-44s%n", title);
        System.out.println("==================================================");
    }
    
    
}