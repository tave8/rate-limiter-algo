package com.giuseppetavella.rate_limiter_algo.most_relevant;

import com.giuseppetavella.rate_limiter_algo.timeline.TimelineRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Measures actual ADMITTED throughput (accepted events/sec) under sustained load,
 * as opposed to raw call throughput (attempts/sec, which includes rejections).
 *
 * Configure the limiter's capacity to whatever real ceiling you want to validate -
 * this test answers "does it actually admit that many per second, sustained."
 */
public class TimelineRateLimiterAdmittedThroughputTest {

    @Test
    @DisplayName("Sustained admitted throughput (accepted/sec) over 30s at real capacity")
    void testSustainedAdmittedThroughput() throws Exception {
        int capacity = 1_500_000;   // set to whatever your real target ceiling is
        long windowMs = 1_000;       // 1-second window -> capacity IS your target events/sec
        int nTimelines = 3;
        int threads = 256;
        int soakSeconds = 10;
        int bucketSeconds = 1;       // per-second buckets so you see the actual sustained rate directly

        TimelineRateLimiter limiter = new TimelineRateLimiter.Builder(capacity, windowMs)
                .nTimelines(nTimelines).build();
        limiter.start();

        AtomicLong[] acceptedPerBucket = new AtomicLong[soakSeconds];
        for (int i = 0; i < soakSeconds; i++) acceptedPerBucket[i] = new AtomicLong(0);
        AtomicLong totalAccepted = new AtomicLong(0);
        AtomicLong totalAttempts = new AtomicLong(0);

        long start = System.currentTimeMillis();
        long end = start + soakSeconds * 1000L;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                while (System.currentTimeMillis() < end) {
                    long elapsed = System.currentTimeMillis() - start;
                    int bucket = Math.min((int) (elapsed / (bucketSeconds * 1000L)), soakSeconds - 1);
                    totalAttempts.incrementAndGet();
                    if (limiter.add()) {
                        totalAccepted.incrementAndGet();
                        acceptedPerBucket[bucket].incrementAndGet();
                    }
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(soakSeconds + 15, TimeUnit.SECONDS);

        System.out.println("\n=== Admitted throughput per " + bucketSeconds + "s bucket ===");
        long minAccepted = Long.MAX_VALUE, maxAccepted = 0;
        for (int i = 0; i < soakSeconds; i++) {
            long v = acceptedPerBucket[i].get();
            minAccepted = Math.min(minAccepted, v);
            maxAccepted = Math.max(maxAccepted, v);
            System.out.printf("  [%2ds]: %,d accepted%n", i, v);
        }

        double acceptRate = 100.0 * totalAccepted.get() / totalAttempts.get();
        System.out.printf("%nConfigured capacity/sec: %,d%n", capacity);
        System.out.printf("Min accepted in a 1s bucket: %,d%n", minAccepted);
        System.out.printf("Max accepted in a 1s bucket: %,d%n", maxAccepted);
        System.out.printf("Total attempts: %,d  Total accepted: %,d (%.2f%% accept rate)%n",
                totalAttempts.get(), totalAccepted.get(), acceptRate);

        // The real claim you want to defend: does every full second actually admit
        // close to the configured capacity, sustained, not just once?
        if (maxAccepted > capacity * 1.005) {
            throw new AssertionError(String.format(
                "A single bucket admitted %,d, exceeding capacity %,d by more than 0.5%%.",
                maxAccepted, capacity));
        }
    }
}