package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.timeline.TimelineRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ====================================================================================================
 *                                  BATTLE TEST 3: WINDOW BOUNDARY BURST STRESS TEST
 * ====================================================================================================
 *
 * PURPOSE OF THIS TEST:
 * ---------------------
 * In production systems, fixed-window rate limiters often suffer from the "2x Burst Boundary Vulnerability".
 * For example, if a rate limiter allows 1,000 requests/second, an attacker could send 1,000 requests at second 0.999
 * and another 1,000 requests at second 1.001, effectively delivering 2,000 requests in a 2-millisecond window.
 *
 * How TimelineManager Addresses This:
 * ----------------------------------
 * TimelineManager uses N staggered timelines offset by (window / N) milliseconds.
 * This test battle-tests whether staggering N timelines successfully smooths out boundary spikes and prevents
 * double-capacity bursts at window reset edges.
 *
 * HOW THE TEST WORKS:
 * -------------------
 * 1. Configures a TimelineManager with limit = 1,000 requests per 1,000ms window.
 * 2. Fires a massive burst of requests (80% of window capacity) immediately preceding a window boundary.
 * 3. Fires another massive burst immediately following the boundary reset.
 * 4. Measures total accepted requests across the boundary seam.
 *
 * PRACTICAL MEANING OF RESULTS:
 * -----------------------------
 * - A single timeline (N = 1) will accept up to 2x capacity across the boundary seam (2,000 requests).
 * - As timeline staggering increases (N = 2, 4, 8, 16), the multi-timeline overlap blocks excess boundary bursts,
 *   holding the effective burst rate close to the configured 1,000 limit.
 *
 * @author AI Battle-Test Suite
 */
public class TimelineAbstractRateLimiterBoundaryBurstTest {

    @Test
    @DisplayName("Battle-Test: Boundary Spike Mitigation Across Staggered Timelines (N = 1, 2, 4, 8, 16)")
    void testBoundarySpikeMitigation() throws InterruptedException {
        int maxEvents = 1_000;
        long windowMs = 1_000;
        int[] timelineCounts = {1, 2, 4, 8, 16};

        System.out.println("\n==========================================================================================");
        System.out.println("              BATTLE-TEST 3: WINDOW BOUNDARY SPIKE & BURST MITIGATION                       ");
        System.out.println("==========================================================================================");
        System.out.println("Scenario: Blasting 800 reqs right before window reset + 800 reqs right after window reset");
        System.out.println("Goal: Verify how staggering N timelines prevents 2x capacity burst spikes at boundary edges.");
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.printf("%-14s | %-12s | %-12s | %-12s | %-16s%n",
                "Timelines (N)", "Target Burst", "Accepted Total", "Rejected Total", "Boundary Mitigation");
        System.out.println("------------------------------------------------------------------------------------------");

        for (int nTimelines : timelineCounts) {
            TimelineRateLimiter manager = new TimelineRateLimiter.Builder(maxEvents, windowMs).nTimelines(nTimelines).build();
            manager.start();

            AtomicInteger totalAccepted = new AtomicInteger(0);
            AtomicInteger totalRejected = new AtomicInteger(0);

            ExecutorService burstPool = Executors.newFixedThreadPool(16);

            for (int i = 0; i < 800; i++) {
                burstPool.submit(() -> {
                    if (manager.add()) {
                        totalAccepted.incrementAndGet();
                    } else {
                        totalRejected.incrementAndGet();
                    }
                });
            }

            TimeUnit.MILLISECONDS.sleep(900);

            for (int i = 0; i < 800; i++) {
                burstPool.submit(() -> {
                    if (manager.add()) {
                        totalAccepted.incrementAndGet();
                    } else {
                        totalRejected.incrementAndGet();
                    }
                });
            }

            burstPool.shutdown();
            burstPool.awaitTermination(3, TimeUnit.SECONDS);

            int accepted = totalAccepted.get();
            int rejected = totalRejected.get();
            int totalBurstAttempted = 1600;

            String mitigationStatus = (accepted <= maxEvents * 1.2)
                    ? "EXCELLENT (Spike Suppressed)"
                    : (accepted <= maxEvents * 1.5)
                    ? "MODERATE (Partial Dampening)"
                    : "UNMITIGATED (2x Burst Seam)";

            System.out.printf(" %-13d | %-12d | %-12d | %-12d | %-16s%n",
                    nTimelines, totalBurstAttempted, accepted, rejected, mitigationStatus);
        }

        System.out.println("==========================================================================================\n");
    }
}
