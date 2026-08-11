// package com.giuseppetavella.rate_limiter_algo.timeline;
//
// import com.giuseppetavella.rate_limiter_algo.Clock;
// import com.giuseppetavella.rate_limiter_algo.TooManyEventsInWindowException;
//
// public class LazyTimeline extends Timeline {
//    
//     public LazyTimeline(int maxEvents, 
//                         TimelineManager manager, 
//                         Clock clock) 
//     {
//         super(maxEvents, manager, clock);
//     }
//    
//    
//     @Override
//     public boolean canAdd(int nEvents) {
//         return hasSpaceForEvents(nEvents);
//     }
//
//
//     @Override
//     public LazyTimeline add() {
//         if(!hasSpaceForEvents(1)) {
//             throw new TooManyEventsInWindowException(maxEvents);
//         }
//        
//         this.countInWindow.getAndIncrement();
//         return this;
//     }
//
//
//     @Override
//     public void wakeup() {
//         // Run the user-defined event filtering logic,
//         // but then find a way to catch thrown exception on the caller,
//         // because caller is a scheduled thread and if not caught
//         // exception would just silently stop the thread 
//        
//         resetCountInWindow();
//         this.windowStart.set(clock.getNow());
//     }
//
//
//     @Override
//     protected boolean filterIn() {
//         return manager.getEventFilterer().apply(this);
//     }
// }
