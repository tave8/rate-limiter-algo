package com.giuseppetavella.rate_limiter_algo;


public class TaskInfo {
    private final Runnable task;
    private final String threadNameToBe;
    private Long threadIdToBe;
    
    public TaskInfo(Runnable task, String threadNameToBe) {
        this.task = task;
        this.threadNameToBe = threadNameToBe;
        this.threadIdToBe = null;
    }
    
    public TaskInfo(Runnable task) {
        this(task, null);    
    }

    public void setThreadIdToBe(long threadIdToBe) {
        if(this.threadIdToBe != null) {
            throw new RuntimeException("cannot set threadId again.");
        }
        this.threadIdToBe = threadIdToBe;
    }

    public long getThreadIdToBe() {
        return this.threadIdToBe;
    }

    public Runnable getTask() {
        return task;
    }

    public String getThreadNameToBe() {
        return threadNameToBe;
    }
}
