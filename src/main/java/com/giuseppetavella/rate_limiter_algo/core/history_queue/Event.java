package com.giuseppetavella.rate_limiter_algo.core.history_queue;

public class Event {
    private final int seq;
    private final long at;
    private final String name;
    private String threadName;
    
    public Event(int seq, long now, String name) {
        this.seq = seq;
        this.at = now;
        this.name = name;
        this.threadName = "?";
    }
    
    public void setThreadName(String thName) {
        this.threadName = thName;
    }
    
    public String getThreadName() {
        return threadName;
    }

    public int getSeq() {
        return seq;
    }

    public long getAt() {
        return at;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Event{" +
                "at=" + at +
                ", seq=" + seq +
                ", name='" + name + '\'' +
                '}';
    }
}