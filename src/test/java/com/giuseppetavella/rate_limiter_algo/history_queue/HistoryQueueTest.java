package com.giuseppetavella.rate_limiter_algo.history_queue;


import com.giuseppetavella.rate_limiter_algo.TooManyEventsInWindowException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HistoryQueueTest {
    
    
    @Test
    void whenAddOnePointInPeriodThenInPeriod() {
        var history = new HistoryQueue(1, 1000);
        history.add();
        
        assertEquals(1, history.countInWindow());
    }

    @Test
    void whenAddOnePointInPeriodAndThenWaitTooLongThenNotInPeriod() {
        var history = new HistoryQueue(1, 1000);
        history.add() // 1
                .after(1050); // 0
        
        assertEquals(0, history.countInWindow());
    }
    

    @Test
    void whenAddTwoPointsInPeriodThenInPeriod() {
        var history = new HistoryQueue(2, 1000);
        history.add() // 1
                .after(950) // 1
                .add(); // 2

        assertEquals(2, history.countInWindow());
    }

    @Test
    void whenAddOnePointInPeriodAndOneNotInPeriodThenOneInPeriodAndOtherNot() {
        var history = new HistoryQueue(1, 1000);
        history.add() // 1
                .after(1050) // 0
                .add(); // 1

        assertEquals(1, history.countInWindow());
    }


    @Test
    void whenAddTwoPointsNotInPeriodThenBothNotInPeriod() {
        var history = new HistoryQueue(2, 1000);
        history.add()  // 1
                .add() // 2
                .after(1050); // 0

        assertEquals(0, history.countInWindow());
    }

    @Test
    void whenAddOnePointInPeriodWithOneMaxItemThenOk() {
        var history = new HistoryQueue(1, 1000);
        history.add(); // 1

        assertEquals(1, history.countInWindow());
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
        history.add() // 1
                .after(1050) // 0
                .add();

        assertEquals(1, history.countInWindow());
    }

    @Test
    void whenAddThreePointsInPeriodWithThreeMaxItemsThenOk() {
        var history = new HistoryQueue(3, 1000);
        history.add() // 1
                .add() // 2
                .add(); // 3

        assertEquals(3, history.countInWindow());
    }

    @Test
    void whenAddOnePointInPeriodAndTwoNotInPeriodWithOneMaxItemThenOk() {
        var history = new HistoryQueue(1, 1000);
        history.add() // 1
                .after(1050) // 0
                .add() // 1
                .after(1050) // 0
                .add(); // 1

        assertEquals(1, history.countInWindow());
    }

    @Test
    void whenAddThreePointsNotInPeriodThenOk() {
        var history = new HistoryQueue(1, 1000);
        history.add() // 1
                .after(1050) // 0
                .add() // 1
                .after(1050) // 0
                .add() // 1
                .after(1050); // 0

        assertEquals(0, history.countInWindow());
    }

    @Test
    void whenAddFiveEventsThenOk() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .add() // 2
                .add() // 3
                .add() // 4
                .add(); // 5

        assertEquals(5, history.countInWindow());
    }

    @Test
    void whenAddFourEventsThenOk() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .add() // 2
                .add() // 3
                .add() // 4
                .after(1050) // 0
                .add(); // 1

        assertEquals(1, history.countInWindow());
    }


    @Test
    void whenAddFiveEventsThenOk_0() {
        var history = new HistoryQueue(5, 1000);
        history.after(1050)
                .add() // 1
                .add() // 2
                .add() // 3
                .add() // 4
                .add(); // 5

        assertEquals(5, history.countInWindow());
    }

    @Test
    void whenAddFiveEventsThenOk_1() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .add() // 2
                .add() // 3
                .add() // 4
                .add() // 5
                .after(950); // 5

        assertEquals(5, history.countInWindow());
    }

    @Test
    void whenAddFiveEventsThenOk_2() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .after(100) // 1
                .add() // 2
                .after(100); // 2 
        
        assertEquals(2, history.countInWindow());
    }

    @Test
    void whenAddFiveEventsThenOk_3() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .after(100) // 1
                .add() // 2
                .after(100) // 2 
                .add() // 3
                .after(100) // 3
                .add() // 4
                .after(100); // 4

        assertEquals(4, history.countInWindow());
    }

    @Test
    void whenAddFiveEventsThenOk_4() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .after(100) // 1
                .add() // 2
                .after(950); // 2-1=1

        assertEquals(1, history.countInWindow());
    }


    @Test
    void whenAddFiveEventsThenOk_5() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .add() // 2
                .after(100) // 2
                .add() // 3
                .after(950); // 3-2=1

        assertEquals(1, history.countInWindow());
    }


    @Test
    void whenAddFiveEventsThenOk_6() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .add() // 2
                .add() // 3
                .add() // 4
                .after(100) // 4
                .add() // 5
                .after(950); // 5-4=1

        assertEquals(1, history.countInWindow());
    }

    @Test
    void test1() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .add() // 2
                .after(100) // 2
                .add() // 3
                .add() // 4
                .add() // 5
                .after(950); // 5-2=3

        assertEquals(3, history.countInWindow());
    }

    @Test
    void test2() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .add() // 2
                .after(100) // 2
                .add() // 3
                .add() // 4
                .after(100) // 4
                .add() // 5
                .after(950); // 5-4=1

        assertEquals(1, history.countInWindow());
    }

    @Test
    void test3() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .add() // 2
                .after(950) // 2
                .add() // 3
                .after(100); // 3-2=1

        assertEquals(1, history.countInWindow());
    }

    @Test
    void test4() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .add() // 2
                .after(950) // 2
                .add() // 3
                .after(100) // 3-2=1
                .add() // 2
                .after(100); // 2

        assertEquals(2, history.countInWindow());
    }

    @Test
    void test5() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .add() // 2
                .after(950) // 2
                .add() // 3
                .after(100) // 3-2=1
                .add() // 2
                .after(100) // 2
                .add() // 3
                .add() // 4
                .add() // 5 
                .after(850); // 5-1=4
        
        assertEquals(4, history.countInWindow());
    }

    @Test
    void test6() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .add() // 2
                .after(950) // 2
                .add() // 3
                .after(100) // 3-2=1
                .add() // 2
                .after(100) // 2
                .add() // 3
                .add() // 4
                .add() // 5 
                .after(950); // 5-2=3

        assertEquals(3, history.countInWindow());
    }


    @Test
    void test7() {
        var history = new HistoryQueue(5, 1000);
        history.add() // 1
                .add() // 2
                .after(950) // 2
                .add() // 3
                .after(100) // 3-2=1
                .add() // 2
                .after(850) // 2
                .add() // 3
                .after(100) // 3-1=2
                .add() // 3
                .add() // 4 
                .after(950) // 4
                .add() // 5
                .after(100); // 5-4=1

        assertEquals(1, history.countInWindow());
    }
    
    @Test
    void test8() {
        
        var history = new HistoryQueue(5, 1000);
        history.add("A") 
                .add("B") 
                .after(950) 
                .add("C") 
                .after(100) 
                .add("D") 
                .after(850) 
                .add("E") 
                .after(100) 
                .add("F") 
                .add("G")  
                .after(100); 

        // history.printPretty();
        
        assertEquals(3, history.countInWindow());
    }
    
    
}