package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.Clock;
import com.giuseppetavella.rate_limiter_algo.ClockImpl;
import com.giuseppetavella.rate_limiter_algo.RejectionReason;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A Timeline is a template providing common functionality 
 * as well as an interface to be implemented (the abstract methods)
 * to the subclassing timelines.
 * The functionality common to all timelines are already implemented by the 
 * methods in this Timeline.
 * 
 * <br><br>
 * 
 * The methods to be implemented, on the other hand, are what make
 * the timelines different. For example, the wakeup 
 * of a Timeline is implementation-specific, just like adding an event
 * is implementation-specific. Whereas checking if there's space for events 
 * is a common functionality. 
 * 
 * <br><br>
 * 
 * Only 4 methods must be implemented by the subclassing timelines:
 * <ul>
 *     <li>
 *         {@code add()}. A subclassing timeline is free to, say, allow adding an event
 *         immediately, or instead do different checks first, trading accuracy for speed.
 *         We can be very flexible and quick at first and do checks later and thus 
 *         be faster but more inaccurate, or answer accurately right now but sacrificing some speed. 
 *     </li>
 *     <li>
 *         {@code canAdd()}. Same goes for asking whether a new event can be added.
 *     </li>
 *     <li>
 *         {@code wakeup()}. If a timeline implementation decides to concentrate event overflow
 *         checks at the timeline wakeup stage instead of performing checks for each new event,
 *         this method will also have different implementations.
 *     </li>
 *     <li>
 *         {@code filterIn()}. The user-defined event filtering logic should have the same implementations.
 *         The caveat, and the reason why it's an abstract method, 
 *         is that to be able to pass the current timeline instance to the user callback, 
 *         and to abstract the timeline away from the user, precisely the current timeline instance must be passed.
 *         And it's only once implemented that the method knows what "this" instance is (the implementation instance), 
 *         which is why this method must be implemented 
 *         and should simply contain {@code return manager.getEventFilterer().apply(this);} in its implementation. 
 *     </li>
 * </ul>
 *
 * In the bigger picture, The Timeline is the key component of the 
 * Timeline-based Rate Limiter implementation. A Timeline
 * does not know or care about scheduling logic, nor should it know
 * or care about other timelines. 
 * 
 * @author Giuseppe Tavella
 */
public abstract class Timeline {
    protected final int maxEvents;
    protected final long window;
    protected final EventFilterer eventFilterer;
    protected final Clock clock;
    protected final byte id;
    
    protected final AtomicLong countInWindow;
    protected final AtomicLong windowStart;

    private RejectionReason rejectionReason;
    /**
     * Template for timelines.
     * 
     * @param maxEvents the max number of events that can be added within {@code window}
     * @param window the amount of time, in milliseconds, in which max {@code maxEvents} can be added
     * @param eventFilterer the user-defined function to decide whether new events can be added or not
     * @param clock an implementation of a clock so that time logic is decoupled from rate limiting logic
     */
    public Timeline(int maxEvents, 
                    long window,
                    Clock clock,
                    EventFilterer eventFilterer,
                    byte id) 
    {
        if(window < 100) {
            throw new IllegalArgumentException("Time window must be >= 100.");
        }
        if(maxEvents < 0) {
            throw new IllegalArgumentException("Max events must be >= 0.");
        }
        if(clock == null) {
            throw new IllegalArgumentException("clock cannot be null in a timeline.");
        }
        if(eventFilterer == null) {
            throw new IllegalArgumentException("eventFilterer cannot be null in a timeline.");
        }
        this.maxEvents = maxEvents;
        this.window = window;
        this.clock = clock;
        this.eventFilterer = eventFilterer;
        this.countInWindow = new AtomicLong(0);
        this.windowStart = new AtomicLong(clock.getNow());
        this.id = id;
    }


    /**
     * Checks if {@code nEvents} can be added to this timeline.
     *
     * @param nEvents number of events
     * @return true if {@code nEvents} events can be added
     */
    public abstract boolean canAdd(int nEvents);

    /**
     * Checks if 1 event can be added to this timeline. 
     * Shortcut for {@code canAdd(1)}.
     * 
     * @return true if it can be added
     */
    public boolean canAdd() {
        return canAdd(1);
    }
    
    /**
     * Adds an event to this timeline or throws custom exception if rejected.
     * Because adding an event is the hotspot of the algorithm,
     * each timeline implementation is free to decide how to add an event.
     * 
     * @return this timeline instance
     */
    public abstract boolean add();

    /**
     * Wakes up this timeline.
     * Waking up is the act of being called by some scheduler at some point in time.
     * Because this is part of the core logic of the algorithm,
     * the implementation must define what waking up a timeline means.
     * When waking up, the subclassing timeline may choose to, say, 
     * just reset the current event count in window, or do checks etc.
     * Again, it's up to the implementing, subclassing timeline 
     * what "waking up" means.
     * 
     * <br><br>
     * 
     * Previous names included "refres" and "reset event count".
     * However, "reset event count" is too specific. 
     * "Refresh" could also be used as a similar term to wake up. 
     */
    public abstract void wakeup();

    /**
     * Evaluates user-defined event filterer on this timeline.
     * It must be implemented because can only know 
     * what "this" current timeline is only once implemented.
     *
     * @return true if event can be added, false if it must be rejected
     */
    protected abstract boolean filterIn();

    /**
     * Evaluates user-defined event filterer on this timeline.
     * The logical opposite of {@code filterIn()}.
     *
     * @return true if event must be rejected, false if it can be added
     */
    protected boolean filterOut() {
        return !filterIn();
    }

    /**
     * Checks whether there is enough remaining capacity for the given number of events.
     *
     * @param nEvents number of events to check
     * @return true if {@code nEvents} can be added, false otherwise
     */
    public boolean hasSpaceForEvents(int nEvents) {
        return countInWindow.get() + nEvents <= maxEvents;
    }

    /**
     * Checks whether there's enough remaining capacity for 1 event.
     * Shortcut for {@code hasSpaceForEvents(1)}.
     *
     * @return true if 1 can be added, false otherwise
     */
    public boolean hasSpaceForEvent() {
        return hasSpaceForEvents(1);
    }

    /**
     * Adding a new event would case event overflow?
     * Shortcut for {@code !hasSpaceForEvent()}.
     * 
     * @return true if adding a new event would cause event overflow
     *          and thus cannot add a new event, false if there's space
     *          for a new event
     */
    public boolean wouldOverflow() {
        return !hasSpaceForEvent();
    }

    /**
     * Resets the current event count in the window.
     * Each timeline keeps track of how many events it has added 
     * since the window start, because at each new window start, the event count is reset,
     * which we do through this method.
     */
    protected void resetCountInWindow() {
        this.countInWindow.set(0);
    }

    /**
     * Is the current event count, over the max events allowed,
     * before {@code breakpoint}, in percentage?
     * 
     * @param breakpoint the value in percentage, scale 0-1
     * @return true if the percentage of current event count over
     *          the max events allowed, is < breakpoint  
     */
    public boolean isBeforeEventThreshold(double breakpoint) {
        double p = (double) getCountInWindow() / maxEvents;
        return p < breakpoint;
    }

    /**
     * Is the current event count, over the max events allowed,
     * after {@code breakpoint}, in percentage?
     *
     * @param breakpoint the value in percentage, scale 0-1
     * @return true if the percentage of current event count over
     *          the max events allowed, is >= breakpoint  
     */
    public boolean isAfterEventTreshold(double breakpoint) {
        return !isBeforeEventThreshold(breakpoint);
    }

    /**
     * Is now, over the defined window, before {@code breakpoint}, in percentage?
     *
     * @param breakpoint the value in percentage, scale 0-1
     * @return true if the percentage of now, over the defined window, is < breakpoint  
     */
    public boolean isBeforeWindowThreshold(double breakpoint) {
        double p = (double) (clock.getNow() - windowStart.get()) / window;
        return p < breakpoint;
    }

    /**
     * Is now, over the defined window, after {@code breakpoint}, in percentage?
     *
     * @param breakpoint the value in percentage, scale 0-1
     * @return true if the percentage of now, over the defined window, is >= breakpoint  
     */
    public boolean isAfterWindowThreshold(double breakpoint) {
        return !isBeforeWindowThreshold(breakpoint);
    }

    /**
     * Each timeline keeps a track of the events added.
     * Specifically, of the events added between now and 
     * the last event count reset (of this timeline).  
     * 
     * @return the count of events added between now and the window start
     */
    public long getCountInWindow() {
        return countInWindow.get();
    }

    /**
     * 
     * @return
     */
    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    /**
     * 
     * @param reason
     */
    protected void setRejectionReason(RejectionReason reason) {
        this.rejectionReason = reason;
    }

    public byte getId() {
        return id;
    }
}