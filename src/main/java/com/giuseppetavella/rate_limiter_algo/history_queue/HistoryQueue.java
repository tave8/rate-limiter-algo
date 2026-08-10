package com.giuseppetavella.rate_limiter_algo.history_queue;


import com.giuseppetavella.rate_limiter_algo.RateLimiter;
import com.giuseppetavella.rate_limiter_algo.TimeUtil;
import com.giuseppetavella.rate_limiter_algo.TooManyEventsInWindowException;

import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Abstract data structure built off a linked list, 
 * to allow keeping track of event times and know whether 
 * a new event could be added based on how far or close 
 * past events are in a given period. 
 * The implementation is task-independent. 
 * <code>maxItemsInPeriod</code> could represent the max number of emails, etc.
 * 
 * Invariants:
 * - Chronology. Let i be the index of the queue; Then each i-th time (ms) in the queue (so for each element)
 *   the following expression always evaluates to true: the time at index i+1 is >= the time at index i
 *   
 * Note:
 * - Any time unit (window, start of window etc.) is always in milliseconds
 * - A time window, simply known as window, represents the amount of time 
 *   in which the user defines that max tasks can be submitted
 * - The history queue does not care whether you use it for task submission or task completion.
 *   In a normal implementation, the user is usually interested in rate limiting 
 *   task submission.
 * 
 * @author Giuseppe Tavella
 */
public class HistoryQueue implements RateLimiter {
    private final LinkedList<Event> queue;
    private final long window;
    // Max items in period
    private final int maxEvents;
    private final TimeUtil util;
    private long cumulativeDelay; // 
    private int nextSeq;
    private long lastLatency;
    // private int manualCountInWindow;
    private final ScheduledExecutorService cleaner;
    private final ReadWriteLock rwQueueLock = new ReentrantReadWriteLock();
    private final Lock readQueueLock = rwQueueLock.readLock();
    private final Lock writeQueueLock = rwQueueLock.writeLock();
    
    public HistoryQueue(int maxEvents, 
                        long window) 
                            throws IllegalArgumentException 
    {
        if(window <= 0) {
            throw new IllegalArgumentException("Time window must be > 0.");
        }
        if(maxEvents < 0) {
            throw new IllegalArgumentException("Max events must be >= 0.");
        }
        this.window = window;
        this.maxEvents = maxEvents;
        this.queue = new LinkedList<>();
        this.util = new TimeUtil(); // This could be injected - ok for now
        this.cumulativeDelay = 0;
        this.nextSeq = 0;
        this.lastLatency = 0;
        // this.manualCountInWindow = 0;
        this.cleaner = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("time-queue-cleaner").factory());
        
        // Schedule thread to clean time queue so it doesn't grow too big
        // cleaner.scheduleAtFixedRate(this::cleanQueue, 0, 1, TimeUnit.MICROSECONDS);
    }
    

    /**
     * The time point can be added only if 
     * the <code>maxItemsInPeriod</code> < <code>period</code>
     *
     */
    public HistoryQueue add(String eventName)
                              throws TooManyEventsInWindowException
    {
        writeQueueLock.lock();
        
        try {

            if(!canAdd()) {
                throw new TooManyEventsInWindowException(maxEvents);
            }

            // Add new event to queue
            // Note how we fake the "now": The now of the event  
            // is not the actual now, but the now plus the cumulative delay
            // at this point in time.
            var candidate = new Event(nextSeq++, getNow(), eventName); 
            candidate.setThreadName(Thread.currentThread().getName()); // Set the name of the thread that added this event
            
            requireInvariantChronology(candidate);
            queue.add(candidate);
            
            return this;

        } finally {
            // this.lastLatency =  
            writeQueueLock.unlock();
        }
    }
    
    public HistoryQueue add(int eventName)
                                throws TooManyEventsInWindowException
    {
        return add(eventName+"");
    }
    
    @Override
    public HistoryQueue add()
                            throws TooManyEventsInWindowException 
    {
        return add("<no name>");
    }


    /**
     * Can I add N events in this time window?
     *
     * @return
     */
    @Override
    public boolean canAdd(int nEvents) {
        readQueueLock.lock();
        
        try {

            // N new events can be added if summing the current 
            // number of events in this time window to the 
            // N desired number of new events, 
            // is <= the number of max events
            return countInWindow() + nEvents <= maxEvents;

        } finally {
            readQueueLock.unlock();
        }
    }

    /**
     * Can I add 1 event in this time window?
     *
     * @return
     */
    public boolean canAdd() {
        return canAdd(1);
    }


    /**
     * Add a delay to the cumulative delay of this history queue.
     * This is the core mechanism of "faking the now" by fast forwarding
     * time with the goal to mimic waiting. When adding an event, 
     * the event is passed the now of the history queue, not the actual now.
     * This decouples the "now of the history queue" from the "actual now",
     * allowing each history queue to have its own concept of time.
     * Both the actual now and the artificial now (history queue) 
     * are abstracted away from the user.
     * 
     * The outcome: We can wait without actually waiting.
     * 
     * @param delay
     * @return
     */
    public HistoryQueue after(long delay) {
        if(delay < 0) {
            throw new IllegalArgumentException("Delay must be >= 0.");
        }
        this.cumulativeDelay += delay;
        return this;
    }
    
    
    /**
     * Was the given time point added in period?
     * 
     * @return
     */
    private boolean inWindow(Event ev) {
        
        readQueueLock.lock();
        
        try {
            
            // The start of this time window is simply substracting
            // the time window from now. By using getNow(), which is 
            // the internal concept of time for each history queue,
            // we don't care whether the now is the actual now or
            // a fake now.
            var windowStart = getNow() - window;
            
            // Invariant "event was never happened after now",
            // so an event has never occurred in the future,
            // and so we don't need to check "AND e.getAt() <= now"
            // because when add an event, we already check that
            // each event respects the chronology invariant
            return ev.getAt() >= windowStart;
            
        } finally {
            readQueueLock.unlock();
        }
        
    }

    /**
     * Get the now of the history queue. 
     * This abstraction allows to fake what now means;
     * We can fake waiting for events, without actually waiting.
     * - When <code>cumulativeDelay = 0</code>, the now of this history queue
     *   correspondes to the actual now (no faking).
     * - When <code>cumulativeDelay > 0</code>, the now of this history queue
     *   has been "fast forwarded" by <code>cumulativeDelay</code>
     * 
     * There's no logical difference between:
     * 
     * <pre>
     * NORMAL WAIT
     *  1. Add event
     *  2. Wait 1 second
     *  3. Add event (this is the current now)
     * 
     * ARTIFICIAL WAIT
     *  1. Add event, Fake wait 1 second, Add event (this is the current now)
     * </pre>
     * 
     * So long as the (artificial) now saved in the event is the (actual) now 
     * plus the cumulative delay at this actual point in time. And this is 
     * precisely the illusion we're creating.
     * 
     * @return
     */
    private long getNow() {
        /**
         * This single line is the whole idea behind fast forwarding time;
         * The now of the history queue is simply the actual now plus 
         * whatever cumulative delay at the actual now.
         */
        return util.getNow() + cumulativeDelay;
    }
    
    
    /**
     * How many events have been added in the time window
     * defined at the history queue level?
     * 
     * @return
     */
    public int countInWindow() {
        
        readQueueLock.lock();
        
        try {
            if(queue.isEmpty()) {
                return 0;
            }
            
            int count = 0;
            int i = queue.size()-1; // Start from last in queue = most recent
            
            while(i >= 0 // So long as there are events AND event is in this time window
                    && inWindow( queue.get(i) )) 
            {  
                count++;
                i--;
            }
            
            return count;
            
        } finally {
            readQueueLock.unlock();
        }
        
    }

    /**
     * Chronology invariant: each time point must be in chronological order.
     * 
     */
    private Event requireInvariantChronology(Event candidate) {
        readQueueLock.lock();
        
        try {
            
            if(queue.isEmpty()) { // Invariant always true
                return candidate;
            }
            
            // queue has events
            var invariantOk = candidate.getAt() >= queue.getLast().getAt(); // Invariant
            
            if(!invariantOk) {
                throw new ChronologyInvariantViolatedException();
            } 
            
        } finally {
            readQueueLock.unlock();
        }
        
        return candidate;
    }
    
    // public int getManualCountInWindow() {
    //     return manualCountInWindow;
    // }
    
    private void cleanQueue() {
        writeQueueLock.lock();
        try {
            // System.out.println("queue before cleaning: " + queue);
            // Remove the elements of the queue that are older
            // than now - period
            queue.removeIf(ev -> !inWindow(ev));

            // System.out.println("queue after cleaning: " + queue);
        } finally {
            writeQueueLock.unlock();
        }
    }

    public void printPretty() {
        if(queue.isEmpty()) {
            System.out.println("Queue has no events.");
            return;
        }
        
        var space = "   ";

        System.out.println("EVENTS:");
        System.out.println("%s # %s | %s N %s | %s W %s | %s dprev %s | %s dnow %s | %s th %s".formatted(
                space, space, space, space, space, space, space, space, space, space, space, space));
        System.out.println("------------------------------------------------------------------------------------");
        
        int i = queue.size()-1;
        while(i >= 0) {
            var ev = queue.get(i);
            var deltaNow = getNow() - ev.getAt();
            
            Event prevEv; long deltaPrev;
            if(i > 0) {
                prevEv = queue.get(i-1);
                deltaPrev = ev.getAt() - prevEv.getAt();
            } else {
                prevEv = null;
                deltaPrev = -1;
            }
            
            
            System.out.println( 
                    "%s %s %s | %s %s %s | %s %s %s | %s %s %s | %s %s %s | %s %s %s".formatted(
                    space, ev.getSeq(), space, 
                    space, ev.getName(), space, 
                    space, inWindow(ev) ? "T" : "F", space, 
                    space, String.format("%05d", deltaPrev), space,
                    space, String.format("%05d", deltaNow), space,
                    space, ev.getThreadName(), space)
            );
            i--;
        }
        System.out.println();
        System.out.println("# = sequence ; N = name ; W = in window? ; \n"
                            +"dprev = delta to previous ; dnow = delta to now ; \n"
                            +"th = thread name \n");
    }



    @Override
    public int getMaxEvents() {
        return maxEvents;
    }

    @Override
    public long getWindow() {
        return window;
    }


    @Override
    public String toString() {
        return "HistoryQueue{" +
                "queue=" + queue +
                '}';
    }
    
    

}
