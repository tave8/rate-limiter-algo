package com.giuseppetavella.rate_limiter_algo.timeline;

import com.giuseppetavella.rate_limiter_algo.TooManyEventsInWindowException;

import java.util.concurrent.atomic.AtomicLong;

public class Timeline {
    private final int maxEvents;
    private AtomicLong countInWindow;
    
    public Timeline(int maxEvents) {
        this.maxEvents = maxEvents;
        this.countInWindow = new AtomicLong(0);
    }
    
    public boolean canAdd(int nEvents) {
        return countInWindow + nEvents <= maxEvents;
    }
    
    public boolean canAdd() {
        return canAdd(1);
    }
    
    public Timeline add() {
        // var currCount = countInWindow.get();
        // while(this.countInWindow.compareAndSet(currCount, currCount+1)) {
        //    
        // }
        
        if(!canAdd()) {
            throw new TooManyEventsInWindowException(maxEvents);
        }
        this.countInWindow.getAndIncrement();
        return this;
    }
    
    public void resetCountInWindow() {
        // Lock-free thread safety
        this.countInWindow.set(0);
    }

    public long getCountInWindow() {
        return countInWindow.get();
    }
}
