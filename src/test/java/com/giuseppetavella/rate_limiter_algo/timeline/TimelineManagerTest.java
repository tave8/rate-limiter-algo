package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.ClockModifier;
import com.giuseppetavella.rate_limiter_algo.ConcurrentModifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class TimelineManagerTest {
    @Test
    void test0() throws InterruptedException {
        var manager = new TimelineManager(1000, 1000, 5).verbose(true);
        var concurrentModifier = new ConcurrentModifier();

        // timeline manager - get max peak for each instance. at add operation.
        // the peak will be compare to the actual number of requests, for example 500 * 20

        int nThreads = 10;

        concurrentModifier
                .concurrently(nThreads, () -> {
                    try {
                        for (int i = 0; i < 10; i++) {
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

        ClockModifier clock = new Clock();
        
        EventFilterer fil = (t) -> {
            return true;
                // if(t.isBeforeWindowThreshold(.8)) {  // Is < 80% of window?
                //     return t.isBeforeEventThreshold(.95); // If < 95% of max events, can add. Else reject.
                // }
                // return t.isBeforeEventThreshold(.97); // If < 97% of window, can add. Else reject.
        }; 
        
        var manager = new TimelineManager(100_000, 
                                  1000, 
                                3, 
                                        fil, 
                                        clock).verbose(true);
        
        var concurrentModifier = new ConcurrentModifier();

        int nThreads = 5000;
        
        Runnable task = () -> {
            try {
                for (int i = 0; i < 20; i++) {
                    manager.add();
                    clock.after(1200);
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

    @Test
    void test9() throws InterruptedException {
        // 100 events / second
        var maxEvents = 100;
        var window = 1000;
        var nTimelines = 3;
        
        EventFilterer fil = (t) -> {
            if(t.isBeforeWindowThreshold(.8)) {
                return t.isBeforeEventThreshold(.95);
            }
            return true;
        };
        
        var manager = new TimelineManager(maxEvents, window, nTimelines, fil);

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

        var eventsPerTask = 10;
        var period = window / 5;
        var random = new Random();
        // 20 * 5 = 100 events / second

        Runnable scheduledTask = () -> {
            var sigma = random.nextInt(-30, 20);
            try {
                for (int i = 0; i < eventsPerTask + sigma; i++) {
                    manager.add();
                }
            } catch (RuntimeException ex) {
                System.out.println(ex.getMessage());
            }
        };

        scheduler.scheduleAtFixedRate(scheduledTask, 0, 550, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(scheduledTask, 350, 200, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(scheduledTask, 800, 980, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(scheduledTask, 1450, 1680, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(scheduledTask, 2200, 2000, TimeUnit.MILLISECONDS);

        Thread.sleep(Duration.ofSeconds(100));

    }
    
}