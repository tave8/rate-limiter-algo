package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.history_queue.HistoryQueue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class TimelineManagerOverflowTest {

    // Helper class to capture individual rejection metadata
    public static class OverflowRecord {
        public final Instant timestamp;
        public final long requestAttemptNumber;
        public final long cumulativeOverflowCount;

        public OverflowRecord(Instant timestamp, long requestAttemptNumber, long cumulativeOverflowCount) {
            this.timestamp = timestamp;
            this.requestAttemptNumber = requestAttemptNumber;
            this.cumulativeOverflowCount = cumulativeOverflowCount;
        }
    } 

    @Test
    void testAndReportExactOverflowMetrics() throws InterruptedException {
        // =================================================================
        // CONFIGURATION
        // =================================================================
        int maxEventsAllowed = 10_000;         // Capacity before triggering rejection
        int windowMs = 1_000;                  // Time window (1 second)
        int nTimelines = 3;                    // Timelines count
        int testDurationSeconds = 5;           // Run duration
        // =================================================================

        EventFilterer fil = (t) -> {
            if (t.isBeforeWindowThreshold(.8)) {
                return t.isBeforeEventThreshold(.95);
            }
            return t.isBeforeEventThreshold(.97);
        };

        // var manager = new TimelineManager(maxEventsAllowed, windowMs, nTimelines, fil);
        var manager = new TimelineManager.Builder(maxEventsAllowed, windowMs, 3).eventFilterer(fil).build();

        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong totalAttempted = new AtomicLong(0);
        AtomicLong totalAccepted = new AtomicLong(0);
        AtomicLong totalRejected = new AtomicLong(0);

        // Thread-safe log to hold overflow details
        ConcurrentLinkedQueue<OverflowRecord> overflowLog = new ConcurrentLinkedQueue<>();

        CountDownLatch startGate = new CountDownLatch(1);

        // Spawn parallel threads flooding manager.add()
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // Synchronize thread start
                    while (running.get()) {
                        long attemptNum = totalAttempted.incrementAndGet();
                        try {
                            manager.add();
                            totalAccepted.incrementAndGet();
                        } catch (RuntimeException ex) {
                            long rejectNum = totalRejected.incrementAndGet();
                            // Capture IF, WHEN, and BY HOW MUCH
                            overflowLog.add(new OverflowRecord(Instant.now(), attemptNum, rejectNum));
                        }
                    }
                } catch (InterruptedException ignored) {
                }
            });
        }

        long startTimeNanos = System.nanoTime();
        startGate.countDown(); // FIRE ALL THREADS

        // Keep driving traffic for configured duration
        Thread.sleep(TimeUnit.SECONDS.toMillis(testDurationSeconds));
        running.set(false);

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        double elapsedSeconds = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0;
        double overallRps = totalAttempted.get() / elapsedSeconds;

        // =================================================================
        // FULL OVERFLOW REPORTING
        // =================================================================
        boolean didOverflow = !overflowLog.isEmpty();
        long totalOverflowAmount = totalRejected.get();

        System.out.println("\n==================================================");
        System.out.println("            OVERFLOW & THROUGHPUT REPORT          ");
        System.out.println("==================================================");
        System.out.printf("Cores Utilized         : %d%n", threads);
        System.out.printf("Execution Time         : %.2f seconds%n", elapsedSeconds);
        System.out.printf("Throughput Achieved    : %,.2f req/sec%n", overallRps);
        System.out.println("--------------------------------------------------");
        System.out.printf("Total Requests Sent    : %,d%n", totalAttempted.get());
        System.out.printf("Total Accepted         : %,d%n", totalAccepted.get());
        System.out.println("--------------------------------------------------");
        System.out.printf("1. DID IT OVERFLOW?    : %s%n", didOverflow ? "YES" : "NO");
        System.out.printf("2. OVERFLOW AMOUNT     : %,d rejected events%n", totalOverflowAmount);

        if (didOverflow) {
            OverflowRecord first = overflowLog.peek();
            System.out.printf("3. FIRST OVERFLOW AT   : %s%n", first.timestamp);
            System.out.printf("   Request # at First  : Attempt %,d%n", first.requestAttemptNumber);
        }
        System.out.println("==================================================\n");

        // Basic sanity check assertions
        assertTrue(totalAttempted.get() > 0, "No requests were executed.");
        assertEquals(totalAttempted.get(), totalAccepted.get() + totalRejected.get(), 
            "Total attempted must equal Accepted + Rejected.");
    }
}