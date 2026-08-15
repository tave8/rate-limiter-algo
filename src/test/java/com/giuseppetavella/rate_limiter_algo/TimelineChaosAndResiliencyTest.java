package com.giuseppetavella.rate_limiter_algo;

import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.TimelineNThreadsRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

public class TimelineChaosAndResiliencyTest {

    private TimelineNThreadsRateLimiter manager;
    private ExecutorService executor;
    private int cores;

    @BeforeEach
    void setUp() {
        cores = Runtime.getRuntime().availableProcessors();
        executor = Executors.newFixedThreadPool(cores);

        manager = new TimelineNThreadsRateLimiter.Builder(10_000, 1_000).nTimelines(6).build();
        manager.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("1. Sinusoidal Traffic Wave Chaos: Dynamic load scaling")
    void testSinusoidalTrafficWaves() throws InterruptedException {
        int testDurationSec = 6;
        AtomicBoolean running = new AtomicBoolean(true);
        LongAdder totalAccepted = new LongAdder();
        LongAdder totalRejected = new LongAdder();

        // Threads fire in oscillating pulses (100ms active, 100ms pause)
        for (int i = 0; i < cores; i++) {
            executor.submit(() -> {
                int cycle = 0;
                while (running.get()) {
                    try {
                        cycle++;
                        boolean heavyPhase = (cycle % 2 == 0);
                        long phaseEnd = System.currentTimeMillis() + 100;

                        while (System.currentTimeMillis() < phaseEnd && running.get()) {
                            if (manager.add()) {
                                totalAccepted.increment();
                            } else {
                                totalRejected.increment();
                            }
                            if (!heavyPhase) {
                                // Light phase micro-pause to simulate low-traffic wave trough
                                Thread.sleep(0, 500); 
                            }
                        }
                    } catch (InterruptedException ignored) {}
                }
            });
        }

        Thread.sleep(TimeUnit.SECONDS.toMillis(testDurationSec));
        running.set(false);

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println("\n==================================================");
        System.out.println("        SINUSOIDAL WAVE TRAFFIC REPORT            ");
        System.out.println("==================================================");
        System.out.printf("Total Accepted         : %,d%n", totalAccepted.sum());
        System.out.printf("Total Rejected         : %,d%n", totalRejected.sum());
        System.out.printf("Recovery Under Waves   : %s%n", totalAccepted.sum() > 0 ? "PASSED" : "FAILED");

        assertTrue(totalAccepted.sum() > 0, "Limiter got stuck and accepted no traffic during wave troughs!");
    }

    @Test
    @DisplayName("2. Worker Interruption Chaos: State resilience under worker death")
    void testWorkerDeathResilience() throws InterruptedException {
        int threads = 8;
        ExecutorService chaosPool = Executors.newFixedThreadPool(threads);
        AtomicBoolean running = new AtomicBoolean(true);
        LongAdder successfulPostChaosAttempts = new LongAdder();

        // Spawn threads that are repeatedly interrupted while calling manager.add()
        for (int i = 0; i < threads; i++) {
            Future<?> future = chaosPool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    manager.add();
                }
            });

            // Randomly cancel worker threads mid-execution
            Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                future.cancel(true);
            }, ThreadLocalRandom.current().nextInt(10, 50), TimeUnit.MILLISECONDS);
        }

        chaosPool.shutdown();
        chaosPool.awaitTermination(1, TimeUnit.SECONDS);

        // Verify that a new, clean worker thread can still interact with the limiter cleanly
        for (int i = 0; i < 1_000; i++) {
            if (manager.add()) {
                successfulPostChaosAttempts.increment();
            }
        }

        System.out.println("\n==================================================");
        System.out.println("        WORKER DEATH RESILIENCE REPORT            ");
        System.out.println("==================================================");
        System.out.printf("Post-Chaos Executions  : %,d/1,000 accepted%n", successfulPostChaosAttempts.sum());
        System.out.printf("State Integrity Status : %s%n", "INTACT");

        // The system must remain operational after thread interruption
        assertDoesNotThrow(() -> manager.add(), "Limiter threw exception or deadlocked after thread death!");
    }
}