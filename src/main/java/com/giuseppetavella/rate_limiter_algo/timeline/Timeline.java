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
        return countInWindow.get() + nEvents <= maxEvents;
    }
    
    public boolean canAdd() {
        return canAdd(1);
    }
    
    public Timeline add() {
        if(!canAdd()) {
            throw new TooManyEventsInWindowException(maxEvents);
        }
        this.countInWindow.getAndIncrement();
        return this;
    }
    
    public void resetCountInWindow() {
        // var currCount = countInWindow.get(); 
        // var overflow = currCount > maxEvents; 
        this.countInWindow.set(0);
        // if(overflow) {
        //     throw new TooManyEventsInWindowException(maxEvents, currCount);
        // }
    }

    public long getCountInWindow() {
        return countInWindow.get();
    }
}
