package com.giuseppetavella.rate_limiter_algo.timeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleHumanReadableTests {

    private TimelineRateLimiter manager;

    @BeforeEach
    void setUp() {
        // Simple setup: 1,000 capacity limit
        manager = new TimelineRateLimiter.Builder(1_000, 1_000).nTimelines(1).build();
        manager.setTimelineSupplier(() -> Timelines.newReactiveQuietBackoffFrom(manager));
        manager.start();
    }

    @Test
    @DisplayName("1. Bouncer Test: Blocks the crowd when full")
    void bouncerTest() {
        int accepted = 0;
        int rejected = 0;

        // Try to push 5,000 requests into a 1,000-capacity door
        for (int i = 0; i < 5_000; i++) {
            if (manager.add()) {
                accepted++;
            } else {
                rejected++;
            }
        }

        System.out.println("\n--- BOUNCER TEST RESULTS ---");
        System.out.println("People trying to enter : 5,000");
        System.out.println("Allowed Inside          : " + accepted);
        System.out.println("Turned Away at Door     : " + rejected);
        System.out.println("Verdict                 : SAFE (No overcrowding)");

        assertTrue(accepted <= 1_000, "Safety violation: Allowed too many!");
    }

    @Test
    @DisplayName("2. Traffic Light Test: Recovers immediately after pause")
    void trafficLightTest() throws InterruptedException {
        // Step 1: Fill up the limit
        for (int i = 0; i < 1_500; i++) {
            manager.add();
        }

        // Step 2: Wait 1.1 seconds for the "road to clear"
        Thread.sleep(1100);

        // Step 3: Send 1 new request
        boolean recovered = manager.add();

        System.out.println("\n--- TRAFFIC LIGHT TEST RESULTS ---");
        System.out.println("Did it open after pause? : " + (recovered ? "YES" : "NO"));
        System.out.println("Verdict                  : " + (recovered ? "RECOVERED INSTANTLY" : "STUCK ON RED"));

        assertTrue(recovered, "System stayed stuck on red after waiting!");
    }
}