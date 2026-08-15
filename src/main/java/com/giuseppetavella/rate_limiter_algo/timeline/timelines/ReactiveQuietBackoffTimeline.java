// package com.giuseppetavella.rate_limiter_algo.timeline.timelines;
//
// import com.giuseppetavella.rate_limiter_algo.AbstractRateLimiter;
// import com.giuseppetavella.rate_limiter_algo.Clock;
// import com.giuseppetavella.rate_limiter_algo.RejectionReason;
// import com.giuseppetavella.rate_limiter_algo.timeline.rate_limiters.AbstractTimelineRateLimiter;
// import com.giuseppetavella.rate_limiter_algo.timeline.EventFilterer;
//
// /**
//  *
//  *
//  */
// public class ReactiveQuietBackoffTimeline extends AbstractTimeline {
//     private final AbstractTimelineRateLimiter manager;
//
//     public ReactiveQuietBackoffTimeline(Builder builder)
//     {
//         if(builder.manager == null) {
//             throw new IllegalArgumentException("when initializing a Lazy Quiet Timeline, "
//                                                 +"its Timeline Manager must be passed, "
//                                                 +"because this timeline implementation requires it to "
//                                                 +"access other timelines in this manager."
//                                                 +"this is an internal error, not a user error.");
//         }
//        
//         // Timeline
//         super(
//                 builder.maxEvents,
//                 builder.window,
//                 builder.clock,
//                 builder.eventFilterer,
//                 builder.id
//         );
//        
//         this.manager = builder.manager; // The manager of this timeline
//     }
//
//
//     /**
//      * Shortcut for applying user-defined filter on this timeline instance.
//      *
//      * @return if true, event must be rejected. if false, event can be added.
//      */
//
//     /**
//      * Can add a new event?
//      *
//      * @param nEvents
//      * @return
//      */
//     @Override
//     public boolean canAdd(int nEvents) {
//         return hasSpaceForEvents(nEvents) && filterIn();
//     }
// 
//
//     /**
//      * Add a new event.
//      *
//      * @return
//      */
//     @Override
//     public boolean add() {
//         if(wouldOverflow()) {
//             setRejectionReason(RejectionReason.WOULD_OVERFLOW);
//             return false;
//         }
//
//         if(filterOut()) {
//             setRejectionReason(RejectionReason.FILTERED_OUT);
//             return false;
//         }
//
//         if(!isPastBackoff()) {
//             setRejectionReason(RejectionReason.BACKOFF);
//             return false;
//         }
//
//         this.countInWindow.getAndIncrement();
//         return true;
//     }
//    
//    
//
//     /**
//      * In this timeline implementation, 
//      * checks are concentrated here in at each wakeup.
//      * 
//      */
//     @Override
//     public void wakeup() {
//         // If a new event cannot be added, 
//         // instead of rejecting the event (which wouldn't make sense
//         // because right now we are at the timeline wakeup
//         // and not adding a new event) set a backoff time,
//         // so that new events cannot be added in this backoff.
//         // This backoff could be the same as 
//         // a window proportional padding.
//         if(wouldOverflow() || filterOut()) {
//            // The backoff must be set for all timelines logically working together
//            // (in a manager)
//             for (byte i = 0; i < manager.getTimelines().size(); i++) {
//                AbstractTimeline t = manager.getTimelines().get(i); 
//                t.setBackoffUntil(clock.getNow() + manager.calcBuffer(1)/6);
//             } 
//         }
//
//         this.countInWindow.set(0);
//         this.windowStart.set(clock.getNow());
//     }
//
//     /**
//      * Shortcut for applying user-defined filter on this timeline instance.
//      *
//      * @return if true, event can be added. if false, event must be rejected.
//      */
//     @Override
//     protected boolean filterIn() {
//         return eventFilterer.filter(this);
//     }
//
//
//
//     public static class Builder {
//         private int maxEvents;
//         private long window;
//         private Clock clock;
//         private EventFilterer eventFilterer;
//         private byte id;
//         private AbstractRateLimiter manager;
//
//         public Builder(int maxEvents, long window, byte id) {
//             this.maxEvents = maxEvents;
//             this.window = window;
//             this.id = id;
//         }
//
//         public static ReactiveQuietBackoffTimeline newFromManager(AbstractTimelineRateLimiter manager) {
//             var builder = new ReactiveQuietBackoffTimeline.Builder(
//                     manager.getMaxEvents(),
//                     manager.getWindow(),
//                     manager.nextTimelineSeq()
//             );
//             builder.clock(manager.getClock());
//             builder.eventFilterer(manager.getEventFilterer());
//             builder.timelineManager(manager); // Set the manager of this timeline
//             return new ReactiveQuietBackoffTimeline(builder);
//         }
//
//         public Builder clock(Clock clock) {
//             this.clock = clock;
//             return this;
//         }
//
//         public Builder eventFilterer(EventFilterer eventFilterer) {
//             this.eventFilterer = eventFilterer;
//             return this;
//         }
//        
//         public Builder timelineManager(AbstractRateLimiter manager) {
//             this.manager = manager;
//             return this;
//         }
//
//         public ReactiveQuietBackoffTimeline build() {
//             return new ReactiveQuietBackoffTimeline(this);
//         }
//     }
//
// }
