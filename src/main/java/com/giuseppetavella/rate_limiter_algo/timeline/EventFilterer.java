package com.giuseppetavella.rate_limiter_algo.timeline;

/**
 * An Event Filterer is a function that gets called before adding a new event, 
 * and allows injecting custom filtering logic. 
 * 
 * <br><br>
 * To use it, all you need to do is define your custom filterer and then 
 * pass it as an argument to the constructor. Like so: 
 * <pre>{@code
 *    // Define custom filter
 *    EventFilterer fil = (t) -> {
 *        if(t.isBeforeWindowThreshold(.8)) {
 *             return t.isBeforeEventThreshold(.95);
 *        }
 *        return true;
 *    };
 *
 *    // Instantiate 
 *    var manager = new TimelineManager(maxEvents, window, nTimelines, fil);
 * }</pre>
 * <br><br>
 * 
 * The Event Filterer is short-circuited AND'd with "is there space to add a new event?", 
 * so the Event Filterer gets executed only if there's still space for new events.
 * 
 * Accepts the current Timeline and returns a boolean.
 * 
 * <ul>
 *     <li>
 *          If true is returned, the new event can be added.        
 *     </li>
 *     <li>
 *         If false is returned, the new event cannot be added.
 *     </li>
 * </ul>
 * 
 * Also: 
 * <ul>
 *     <li>
 *         Breakpoints are always in percentage, scale 0-1.
 *     </li>
 *     <li>
 *         Is before and is after. Before is < (exclusive), after is >= (inclusive). 
 *     </li>
 * </ul>
 *
 * <br>
 * 
 * Here's a useful case of an Event Filterer that acts as a Burst Protector.
 * It means "If we are within 80% of the window and we've already added 
 * more than 95% of max events, reject new events. In any other case, you can add new event.".
 * 
 * 
 * <pre>{@code
 *     EventFilterer fil = (t) -> {
 *          if(t.isBeforeWindowThreshold(.8)) {  // Is < 80% of window?
 *              return t.isBeforeEventThreshold(.95); // If < 95% of max events, can add. Else reject.
 *          }
 *          return true; // If >= 80% of window, can add.
 *     };
 * }
 * </pre>
 * 
 * Or this more preventive implementation: 
 *
 * <pre>{@code
 *     EventFilterer fil = (t) -> {
 *          if(t.isBeforeWindowThreshold(.8)) {  // Is < 80% of window?
 *              return t.isBeforeEventThreshold(.95); // If < 95% of max events, can add. Else reject.
 *          }
 *          return t.isBeforeEventThreshold(.97); // If < 97% of window, can add. Else reject.
 *     };
 * }
 * </pre> 
 * <br>

 *
 * @author Giuseppe Tavella
 */
@FunctionalInterface
public interface EventFilterer {
    /**
     * Abstracts away the timelines into a single method 
     * that passes a timeline one at a time.
     * The user does not have to care about how many timelines 
     * logically work together, or when or how each timeline
     * has been called.
     * 
     * 
     * @param t a timeline of the timelines logically working together 
     *          (in a manager, for example)
     * @return true if the new event can be added, 
     *          false if the new event cannot be added
     */
    boolean filter(Timeline t);
}