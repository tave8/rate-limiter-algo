package com.giuseppetavella.rate_limiter_algo;


import java.util.ArrayList;
import java.util.List;

public class ConcurrentModifier {
    private final List<TaskInfo> tasks;
    
    public ConcurrentModifier() {
        this.tasks = new ArrayList<>();
    }
    
    public ConcurrentModifier concurrently(Runnable task, 
                                           String threadNameToBe) {
        tasks.add(new TaskInfo(task, threadNameToBe));
        return this;
    }

    public ConcurrentModifier concurrently(Runnable task,
                                           int threadNameToBe) {
        return concurrently(task, threadNameToBe+"");
    }

    public ConcurrentModifier concurrently(Runnable task) {
        return concurrently(task, null);
    }
    
    public void useRawThreads() {
        
        List<Thread> threads = new ArrayList<>();
        
        // For each task, a thread is created
        for (var taskInfo : tasks) {
            var thread = new Thread(() -> {
                // The thread that is running this task
                // must truly be the thread that was promised to run it
                assert Thread.currentThread().threadId() == taskInfo.getThreadIdToBe();
                taskInfo.getTask().run();  
            });
            
            taskInfo.setThreadIdToBe(thread.threadId());
                    
            if(taskInfo.getThreadNameToBe() != null) { // Set the thread name only if one was provided
                thread.setName(taskInfo.getThreadNameToBe());
            }
            
            threads.add(thread);
            thread.start();
        }

        for(var thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }
    
    
}
