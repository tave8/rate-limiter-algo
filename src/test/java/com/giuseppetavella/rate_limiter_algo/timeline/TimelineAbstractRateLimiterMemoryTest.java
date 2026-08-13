package com.giuseppetavella.rate_limiter_algo.timeline;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.GraphLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TimelineAbstractRateLimiterMemoryTest {

    @Test
    void measureTimelineManagerMemoryUnderLoad() throws InterruptedException {
        EventFilterer filterer = new EventFilterer() {
            @Override
            public boolean filter(Timeline t) {
                if (t.isBeforeWindowThreshold(.8)) {
                    return t.isBeforeEventThreshold(.95);
                }
                return t.isBeforeEventThreshold(.63);
            }
        };
        
        var manager = new TimelineRateLimiter.Builder(10_000, 1_000)
                .eventFilterer(filterer).build();
        
        // manager.setTimelineSupplier(() -> ReactiveQuietTimeline.Builder.fromManager(manager));
        manager.start();



        // 1. Initial footprint check
        GraphLayout initialLayout = GraphLayout.parseInstance(manager);
        long initialSize = initialLayout.totalSize();

        // 2. Drive 1,000,000 requests across parallel threads
        int totalRequests = 1_000_000;
        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        AtomicLong acceptedCount = new AtomicLong(0);
        AtomicLong rejectedCount = new AtomicLong(0);

        long startTimeNanos = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                if (manager.add()) {
                    acceptedCount.incrementAndGet();
                } else {
                    rejectedCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);

        // 3. Post-load footprint check
        GraphLayout postLoadLayout = GraphLayout.parseInstance(manager);
        long postLoadSize = postLoadLayout.totalSize();

        // =================================================================
        // REPORTING
        // =================================================================
        System.out.println("\n==========================================");
        System.out.println("  MEMORY & LOAD TEST REPORT (1M REQUESTS) ");
        System.out.println("==========================================");
        System.out.printf("Completed in Time    : %d ms%n", elapsedMillis);
        System.out.printf("Total Requests Processed: %,d%n", totalRequests);
        System.out.printf("Accepted             : %,d%n", acceptedCount.get());
        System.out.printf("Rejected             : %,d%n", rejectedCount.get());
        System.out.println("------------------------------------------");
        System.out.printf("Initial Memory Size  : %,d bytes (~%.2f KB)%n",
                initialSize, initialSize / 1024.0);
        System.out.printf("Post-Load Memory Size: %,d bytes (~%.2f KB)%n",
                postLoadSize, postLoadSize / 1024.0);
        System.out.printf("Heap Growth          : %,d bytes%n",
                (postLoadSize - initialSize));
        System.out.println("------------------------------------------");
        System.out.println("Post-Load Class Footprint Breakdown:");
        System.out.println(postLoadLayout.toFootprint());
    }
}