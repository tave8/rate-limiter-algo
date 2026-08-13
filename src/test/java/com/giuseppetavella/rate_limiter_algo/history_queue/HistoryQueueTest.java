package com.giuseppetavella.rate_limiter_algo.history_queue;


import com.giuseppetavella.rate_limiter_algo.AbstractRateLimiter;
import com.giuseppetavella.rate_limiter_algo.TooManyEventsInWindowException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HistoryQueueTest {


    @Test
    void whenAddOnePointInPeriodThenInPeriod() {
        var history = new HistoryQueue(1, 1000);
        history.add();

        assertEquals(1, history.getCountInWindow());
    }

    @Test
    void whenAddOnePointInPeriodAndThenWaitTooLongThenNotInPeriod() {
        AbstractRateLimiter history = new HistoryQueue(1, 1000);
        history.add(); // 1
        history.after(1050); // 0

        assertEquals(0, history.getCountInWindow());
    }


    @Test
    void whenAddTwoPointsInPeriodThenInPeriod() {
        var history = new HistoryQueue(2, 1000);
        history.add(); // 1
        history.after(950); // 1
        history.add(); // 2

        assertEquals(2, history.getCountInWindow());
    }

    @Test
    void whenAddOnePointInPeriodAndOneNotInPeriodThenOneInPeriodAndOtherNot() {
        var history = new HistoryQueue(1, 1000);
        history.add(); // 1
        history.after(1050); // 0
        history.add(); // 1

        assertEquals(1, history.getCountInWindow());
    }


    @Test
    void whenAddTwoPointsNotInPeriodThenBothNotInPeriod() {
        var history = new HistoryQueue(2, 1000);
        history.add();  // 1
        history.add(); // 2
        history.after(1050); // 0

        assertEquals(0, history.getCountInWindow());
    }

    @Test
    void whenAddOnePointInPeriodWithOneMaxItemThenOk() {
        var history = new HistoryQueue(1, 1000);
        history.add(); // 1

        assertEquals(1, history.getCountInWindow());
    }

    @Test
    void whenAddTwoPointsInPeriodWithOneMaxItemThenThrow() {
        var history = new HistoryQueue(1, 1000);
        history.add(); // 1

        assertThrows(TooManyEventsInWindowException.class, () -> {
            history.add();
        });
    }

    @Test
    void whenAddOnePointInPeriodAndOneNotInPeriodWithOneMaxItemThenOk() {
        var history = new HistoryQueue(1, 1000);
        history.add(); // 1
        history.after(1050); // 0
        history.add();

        assertEquals(1, history.getCountInWindow());
    }

    @Test
    void whenAddThreePointsInPeriodWithThreeMaxItemsThenOk() {
        var history = new HistoryQueue(3, 1000);
        history.add(); // 1
        history.add(); // 2
        history.add(); // 3

        assertEquals(3, history.getCountInWindow());
    }

    @Test
    void whenAddOnePointInPeriodAndTwoNotInPeriodWithOneMaxItemThenOk() {
        var history = new HistoryQueue(1, 1000);
        history.add(); // 1
        history.after(1050); // 0
        history.add(); // 1
        history.after(1050); // 0
        history.add(); // 1

        assertEquals(1, history.getCountInWindow());
    }

    @Test
    void whenAddThreePointsNotInPeriodThenOk() {
        var history = new HistoryQueue(1, 1000);
        history.add(); // 1
        history.after(1050); // 0
        history.add(); // 1
        history.after(1050); // 0
        history.add(); // 1
        history.after(1050); // 0

        assertEquals(0, history.getCountInWindow());
    }

    @Test
    void whenAddFiveEventsThenOk() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.add(); // 2
        history.add(); // 3
        history.add(); // 4
        history.add(); // 5

        assertEquals(5, history.getCountInWindow());
    }

    @Test
    void whenAddFourEventsThenOk() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.add(); // 2
        history.add(); // 3
        history.add(); // 4
        history.after(1050); // 0
        history.add(); // 1

        assertEquals(1, history.getCountInWindow());
    }


    @Test
    void whenAddFiveEventsThenOk_0() {
        var history = new HistoryQueue(5, 1000);
        history.after(1050);
        history.add(); // 1
        history.add(); // 2
        history.add(); // 3
        history.add(); // 4
        history.add(); // 5

        assertEquals(5, history.getCountInWindow());
    }

    @Test
    void whenAddFiveEventsThenOk_1() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.add(); // 2
        history.add(); // 3
        history.add(); // 4
        history.add(); // 5
        history.after(950); // 5

        assertEquals(5, history.getCountInWindow());
    }

    @Test
    void whenAddFiveEventsThenOk_2() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.after(100); // 1
        history.add(); // 2
        history.after(100); // 2 

        assertEquals(2, history.getCountInWindow());
    }

    @Test
    void whenAddFiveEventsThenOk_3() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.after(100); // 1
        history.add(); // 2
        history.after(100); // 2 
        history.add(); // 3
        history.after(100); // 3
        history.add(); // 4
        history.after(100); // 4

        assertEquals(4, history.getCountInWindow());
    }

    @Test
    void whenAddFiveEventsThenOk_4() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.after(100); // 1
        history.add(); // 2
        history.after(950); // 2-1=1

        assertEquals(1, history.getCountInWindow());
    }


    @Test
    void whenAddFiveEventsThenOk_5() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.add(); // 2
        history.after(100); // 2
        history.add(); // 3
        history.after(950); // 3-2=1

        assertEquals(1, history.getCountInWindow());
    }


    @Test
    void whenAddFiveEventsThenOk_6() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.add(); // 2
        history.add(); // 3
        history.add(); // 4
        history.after(100); // 4
        history.add(); // 5
        history.after(950); // 5-4=1

        assertEquals(1, history.getCountInWindow());
    }

    @Test
    void test1() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.add(); // 2
        history.after(100); // 2
        history.add(); // 3
        history.add(); // 4
        history.add(); // 5
        history.after(950); // 5-2=3

        assertEquals(3, history.getCountInWindow());
    }

    @Test
    void test2() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.add(); // 2
        history.after(100); // 2
        history.add(); // 3
        history.add(); // 4
        history.after(100); // 4
        history.add(); // 5
        history.after(950); // 5-4=1

        assertEquals(1, history.getCountInWindow());
    }

    @Test
    void test3() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.add(); // 2
        history.after(950); // 2
        history.add(); // 3
        history.after(100); // 3-2=1

        assertEquals(1, history.getCountInWindow());
    }

    @Test
    void test4() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.add(); // 2
        history.after(950); // 2
        history.add(); // 3
        history.after(100); // 3-2=1
        history.add(); // 2
        history.after(100); // 2

        assertEquals(2, history.getCountInWindow());
    }

    @Test
    void test5() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.add(); // 2
        history.after(950); // 2
        history.add(); // 3
        history.after(100); // 3-2=1
        history.add(); // 2
        history.after(100); // 2
        history.add(); // 3
        history.add(); // 4
        history.add(); // 5 
        history.after(850); // 5-1=4

        assertEquals(4, history.getCountInWindow());
    }

    @Test
    void test6() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.add(); // 2
        history.after(950); // 2
        history.add(); // 3
        history.after(100); // 3-2=1
        history.add(); // 2
        history.after(100); // 2
        history.add(); // 3
        history.add(); // 4
        history.add(); // 5 
        history.after(950); // 5-2=3

        assertEquals(3, history.getCountInWindow());
    }


    @Test
    void test7() {
        var history = new HistoryQueue(5, 1000);
        history.add(); // 1
        history.add(); // 2
        history.after(950); // 2
        history.add(); // 3
        history.after(100); // 3-2=1
        history.add(); // 2
        history.after(850); // 2
        history.add(); // 3
        history.after(100); // 3-1=2
        history.add(); // 3
        history.add(); // 4 
        history.after(950); // 4
        history.add(); // 5
        history.after(100); // 5-4=1

        assertEquals(1, history.getCountInWindow());
    }

    @Test
    void test8() {

        var history = new HistoryQueue(5, 1000);
        history.add("A");
        history.add("B");
        history.after(950);
        history.add("C");
        history.after(100);
        history.add("D");
        history.after(850);
        history.add("E");
        history.after(100);
        history.add("F");
        history.add("G");
        history.after(100);

        // history.printPretty();

        assertEquals(3, history.getCountInWindow());
    }


}