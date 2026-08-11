package com.giuseppetavella.rate_limiter_algo.history_queue;


import com.giuseppetavella.rate_limiter_algo.ConcurrentModifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HistoryQueueConcurrencyTest {

    @Test
    void testConcurrent1() {

        var history = new HistoryQueue(3, 1000);

        var concurrentModifier = new ConcurrentModifier();
        concurrentModifier
                .concurrently(() -> {
                    history.add("A");
                    history.add("B");
                })
                .concurrently(() -> {
                    history.add("C");
                })
                .useRawThreads();

        assertEquals(3, history.countInWindow());
    } 

    @Test
    void testConcurrent2() {

        var history = new HistoryQueue(3, 1000);

        var concurrentModifier = new ConcurrentModifier();
        concurrentModifier
                .concurrently(() -> {
                    history.add(1);
                    history.add(2);
                }, 1)
                .concurrently(() -> {
                    history.add(1);
                    if(history.countInWindow() == 3) {
                        assertFalse(history.canAdd());
                    }
                }, 2)
                .useRawThreads();
        
        history.printPretty();

        assertEquals(3, history.countInWindow());
    }

    @Test
    void testConcurrent3() {

        var history = new HistoryQueue(5, 1000);

        var concurrentModifier = new ConcurrentModifier();
        concurrentModifier
                .concurrently(() -> {
                    history.add("A");
                    history.add("B");
                }, "1")
                .concurrently(() -> {
                    history.add("C");
                    if(history.countInWindow() == 5) {
                        assertFalse(history.canAdd());
                    }
                }, "2")
                .concurrently(() -> {
                    history.add("D");
                    history.add("F");
                }, "3")
                .useRawThreads();

        history.printPretty();

        assertEquals(5, history.countInWindow());
    }

    @Test
    void testConcurrent6() {

        var history = new HistoryQueue(5, 1000);

        var concurrentModifier = new ConcurrentModifier();
        concurrentModifier
                .concurrently(() -> {
                    history.add("A");
                    if(history.countInWindow() == 5) {
                        assertFalse(history.canAdd());
                    }
                }, 1)
                .concurrently(() -> {
                    history.add("B");
                    if(history.countInWindow() == 5) {
                        assertFalse(history.canAdd());
                    }
                }, 2)
                .concurrently(() -> {
                    history.add("C");
                    if(history.countInWindow() == 5) {
                        assertFalse(history.canAdd());
                    }
                }, 3)
                .concurrently(() -> {
                    history.add("D");
                    if(history.countInWindow() == 5) {
                        assertFalse(history.canAdd());
                    }
                }, 4)
                .concurrently(() -> {
                    history.add("E");
                    if(history.countInWindow() == 5) {
                        assertFalse(history.canAdd());
                    }
                }, 5)
                .useRawThreads();

        history.printPretty();
        
        
        assertEquals(5, history.countInWindow());
    }

    @Test
    void testConcurrent7() {

        var history = new HistoryQueue(5, 1000);

        var concurrentModifier = new ConcurrentModifier();
        concurrentModifier
                .concurrently(() -> {
                    history.add(1);
                    history.add(2);
                    history.add(3);
                    if(history.countInWindow() == 5) {
                        assertFalse(history.canAdd());
                    }
                }, 1)
                .concurrently(() -> {
                    history.add(4);
                    if(history.countInWindow() == 5) {
                        assertFalse(history.canAdd());
                    }
                }, 2)
                .concurrently(() -> {
                    history.add(5);
                    if(history.countInWindow() == 5) {
                        assertFalse(history.canAdd());
                    }
                }, 3)
                .concurrently(() -> {
                    if(history.countInWindow() == 5) {
                        assertFalse(history.canAdd());
                    }
                }, 4)
                .useRawThreads();

        history.printPretty();
        
        assertEquals(5, history.countInWindow());
    }

    @Test
    void testConcurrent8() {

        var history = new HistoryQueue(10, 1000);

        var concurrentModifier = new ConcurrentModifier();
        concurrentModifier
                .concurrently(() -> {
                    history.add(1);
                    history.add(2);
                    history.add(3);
                    if(history.countInWindow() == 10) {
                        assertFalse(history.canAdd());
                    }
                }, 1)
                .concurrently(() -> {
                    history.add(4);
                    if(history.countInWindow() == 10) {
                        assertFalse(history.canAdd());
                    }
                }, 2)
                .concurrently(() -> {
                    history.add(5);
                    if(history.countInWindow() == 10) {
                        assertFalse(history.canAdd());
                    }
                }, 3)
                .concurrently(() -> {
                    if(history.countInWindow() == 10) {
                        assertFalse(history.canAdd());
                    }
                    history.add(6);
                }, 4)
                .concurrently(() -> {
                    if(history.countInWindow() == 10) {
                        assertFalse(history.canAdd());
                    }
                    history.add(7);
                }, 5)
                .concurrently(() -> {
                    if(history.countInWindow() == 10) {
                        assertFalse(history.canAdd());
                    }
                    history.add(8);
                    history.add(9);
                    history.add(10);
                }, 6)
                .useRawThreads();

        history.printPretty();

        assertEquals(10, history.countInWindow());
    }
}
