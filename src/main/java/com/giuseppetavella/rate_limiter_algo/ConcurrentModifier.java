package com.giuseppetavella.rate_limiter_algo;


import java.util.ArrayList;
import java.util.List;

public class ConcurrentModifier {
    private final List<TaskInfo> tasks;
    
    public ConcurrentModifier() {
        this.tasks = new ArrayList<>();
    }

    /**
     * Each task will be run a different thread.
     * The threads will run concurrently.
     * 
     * @param task
     * @param threadNameToBe
     * @return
     */
    public ConcurrentModifier concurrently(Runnable task, 
                                           String threadNameToBe) {
        tasks.add(new TaskInfo(task, threadNameToBe));
        return this;
    }

    /**
     * Each task will be run a different thread.
     * The threads will run concurrently.
     *
     * @param task
     * @param threadNameToBe
     * @return
     */
    public ConcurrentModifier concurrently(Runnable task,
                                           int threadNameToBe) {
        return concurrently(task, threadNameToBe+"");
    }

    /**
     * Each task will be run a different thread.
     * The threads will run concurrently.
     *
     * @param task
     * @return
     */
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
                if(Thread.currentThread().threadId() != taskInfo.getThreadIdToBe()) {
                    throw new RuntimeException("The thread that was promised to run this task "
                                              +"is not the thread that actually run it.");
                };
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
