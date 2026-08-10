package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.ConcurrentModifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TimelineManagerTest {
    @Test
    void test1() throws InterruptedException {
        var manager = new TimelineManager(100_000, 1000, 5);
        var concurrentModifier = new ConcurrentModifier();
        
        // timeline manager - get max peak for each instance. at add operation.
        // the peak will be compare to the actual number of requests, for example 500 * 20

        int nThreads = 1000;
        
        concurrentModifier
            .concurrently(nThreads, () -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        manager.add();
                    } 
                } catch (RuntimeException ex) {
                    System.out.println(ex.getMessage());
                }
            })
            .useRawThreads();
        
        Thread.sleep(Duration.ofSeconds(10));
        
    }

    @Test
    void test2() throws InterruptedException {
        var manager = new TimelineManager(100_000, 1000, 5);
        var concurrentModifier = new ConcurrentModifier();

        int nThreads = 100;

        concurrentModifier
                .concurrently(nThreads, () -> {
                    try {
                        for (int i = 0; i < 1_000; i++) {
                            manager.add();
                        }
                    } catch (RuntimeException ex) {
                        System.out.println(ex.getMessage());
                    }
                })
                .useRawThreads();

        Thread.sleep(Duration.ofSeconds(10));

    }


    @Test
    void test3() throws InterruptedException {
        var manager = new TimelineManager(100_000, 1000, 5);
        var concurrentModifier = new ConcurrentModifier();

        int nThreads = 10;

        concurrentModifier
                .concurrently(nThreads, () -> {
                    try {
                        for (int i = 0; i < 10_000; i++) {
                            manager.add();
                        }
                    } catch (RuntimeException ex) {
                        System.out.println(ex.getMessage());
                    }
                })
                .useRawThreads();

        Thread.sleep(Duration.ofSeconds(10));

    }

    @Test
    void test4() throws InterruptedException {
        var manager = new TimelineManager(100_000, 1000, 5);
        var concurrentModifier = new ConcurrentModifier();

        int nThreads = 100;

        concurrentModifier
                .concurrently(nThreads, () -> {
                    try {
                        for (int i = 0; i < 200; i++) {
                            manager.add();
                            manager.add();
                            manager.add();
                            manager.add();
                            manager.add();
                        }
                    } catch (RuntimeException ex) {
                        System.out.println(ex.getMessage());
                    }
                })
                .useRawThreads();

        Thread.sleep(Duration.ofSeconds(10));

    }

    @Test
    void test5() throws InterruptedException {
        var manager = new TimelineManager(100_000, 1000, 5);
        var concurrentModifier = new ConcurrentModifier();

        int nThreads = 1000;

        concurrentModifier
                .concurrently(nThreads, () -> {
                    try {
                        for (int i = 0; i < 10; i++) {
                            manager.add();
                            manager.add();
                            manager.add();
                            manager.add();
                            manager.add();
                            manager.add();
                            manager.add();
                            manager.add();
                            manager.add();
                            manager.add();
                        }
                    } catch (RuntimeException ex) {
                        System.out.println(ex.getMessage());
                    }
                })
                .useRawThreads();

        Thread.sleep(Duration.ofSeconds(10));

    }

    @Test
    void test6() throws InterruptedException {
        var manager = new TimelineManager(100_000, 1000, 5);
        var concurrentModifier = new ConcurrentModifier();

        int nThreads = 2_000;

        concurrentModifier
                .concurrently(nThreads, () -> {
                    try {
                        for (int i = 0; i < 25; i++) {
                            manager.add();
                            manager.add();
                        }
                    } catch (RuntimeException ex) {
                        System.out.println(ex.getMessage());
                    }
                })
                .useRawThreads();

        Thread.sleep(Duration.ofSeconds(10));

    }


    @Test
    void test7() throws InterruptedException {
        var manager = new TimelineManager(100_000, 1000, 3);
        var concurrentModifier = new ConcurrentModifier();

        int nThreads = 5000;
        
        Runnable task = () -> {
            try {
                for (int i = 0; i < 20; i++) {
                    manager.add();
                }
            } catch (RuntimeException ex) {
                System.out.println(ex.getMessage());
            }  
        };
        
        Runnable scheduledTask = () -> {
            concurrentModifier
                    .concurrently(nThreads, task)
                    .useRawThreads();
        };

        Executors.newSingleThreadScheduledExecutor()
                        .scheduleAtFixedRate(scheduledTask, 0, 2, TimeUnit.SECONDS);
        

        Thread.sleep(Duration.ofSeconds(100));

    }


    @Test
    void test8() throws InterruptedException {
        // 100 events / second
        var maxEvents = 100;
        var window = 1000;
        var nTimelines = 3;
        var manager = new TimelineManager(maxEvents, window, nTimelines);
        
        var concurrentModifier = new ConcurrentModifier();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        
        // int nThreads = 5000;
        //
        // Runnable task = () -> {
        //     try {
        //         for (int i = 0; i < 20; i++) {
        //             manager.add();
        //         }
        //     } catch (RuntimeException ex) {
        //         System.out.println(ex.getMessage());
        //     }
        // };

        var eventsPerTask = 20; 
        var period = window / 5;
        var random = new Random();
        // 20 * 5 = 100 events / second
        
        Runnable scheduledTask = () -> {
            var sigma = random.nextInt(-3, 4);
            try {
                for (int i = 0; i < eventsPerTask + sigma; i++) {
                    manager.add();
                }
            } catch (RuntimeException ex) {
                System.out.println(ex.getMessage());
            }
        };

        scheduler.scheduleAtFixedRate(scheduledTask, 0, period, TimeUnit.MILLISECONDS);

        Thread.sleep(Duration.ofSeconds(100));

    }
}