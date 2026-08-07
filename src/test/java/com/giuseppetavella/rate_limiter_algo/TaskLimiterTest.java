package com.giuseppetavella.rate_limiter_algo;// package com.giuseppetavella.unit;
//
// import com.giuseppetavella.core.TaskLimiter;
// import com.giuseppetavella.exceptions.TooManyEventsInWindowException;
// import org.junit.jupiter.api.Test;
//
// import java.time.Duration;
// import java.util.concurrent.Callable;
// import java.util.concurrent.ExecutionException;
// import java.util.concurrent.Executors;
// import java.util.concurrent.Future;
//
// import static org.junit.jupiter.api.Assertions.*;
//
// class TaskLimiterTest {
//     @Test
//     void whenSubmitOneTaskInPeriodThenOk() throws ExecutionException, InterruptedException {
//         var executor = Executors.newVirtualThreadPerTaskExecutor();
//         var taskLimiter = new TaskLimiter(1, 1000, executor);
//         Callable<String> task = () -> {
//             return "finished task";
//         };
//        
//         Future<String> future = taskLimiter.submitOrThrow(task);
//        
//         assertEquals("finished task", future.get());
//     }
//
//     @Test
//     void whenSubmitTwoTasksInPeriodThenOk() throws ExecutionException, InterruptedException {
//         var executor = Executors.newVirtualThreadPerTaskExecutor();
//         var taskLimiter = new TaskLimiter(2, 1000, executor);
//         Callable<String> task1 = () -> {
//             return "finished task1";
//         };
//         Callable<String> task2 = () -> {
//             return "finished task2";
//         };
//        
//         Future<String> future1 = taskLimiter.submitOrThrow(task1);
//         Future<String> future2 = taskLimiter.submitOrThrow(task2);
//
//         assertEquals("finished task1", future1.get());
//         assertEquals("finished task2", future2.get());
//     }
//
//     @Test
//     void whenSubmitThreeTasksInPeriodThenTooMany() throws ExecutionException, InterruptedException {
//         var executor = Executors.newVirtualThreadPerTaskExecutor();
//         var taskLimiter = new TaskLimiter(2, 1000, executor);
//         Callable<String> task1 = () -> {
//             return "finished task1";
//         };
//         Callable<String> task2 = () -> {
//             return "finished task2";
//         };
//         Callable<String> task3 = () -> {
//             return "finished task3";
//         };
//
//         Future<String> future1 = taskLimiter.submitOrThrow(task1);
//         Future<String> future2 = taskLimiter.submitOrThrow(task2);
//        
//         assertThrows(TooManyEventsInWindowException.class, () -> {
//             Future<String> future3 = taskLimiter.submitOrThrow(task3);
//         });
//
//         assertEquals("finished task1", future1.get());
//         assertEquals("finished task2", future2.get());
//        
//     }
//
//     @Test
//     void whenSubmitTwoTasksInPeriodAndOneNotInPeriodThenOk() throws ExecutionException, InterruptedException {
//         var executor = Executors.newVirtualThreadPerTaskExecutor();
//         var taskLimiter = new TaskLimiter(2, 1000, executor);
//         Callable<String> task1 = () -> {
//             // System.out.println("executing task1");
//             return "finished task1";
//         };
//         Callable<String> task2 = () -> {
//             // System.out.println("executing task2");
//             return "finished task2";
//         };
//         Callable<String> task3 = () -> {
//             // System.out.println("executing task3");
//             return "finished task3";
//         };
//
//         Future<String> future1 = taskLimiter.submitOrThrow(task1);
//         Future<String> future2 = taskLimiter.submitOrThrow(task2);
//         Thread.sleep(Duration.ofMillis(1050));
//         Future<String> future3 = taskLimiter.submitOrThrow(task3);
//
//         assertEquals("finished task1", future1.get());
//         assertEquals("finished task2", future2.get());
//         assertEquals("finished task3", future3.get());
//
//     }
//
//     @Test
//     void whenSubmitFiveTasksInPeriodAndOneNotInPeriodThenOk() throws ExecutionException, InterruptedException {
//         var executor = Executors.newVirtualThreadPerTaskExecutor();
//         var taskLimiter = new TaskLimiter(5, 1000, executor);
//         Callable<String> task1 = () -> {
//             Thread.sleep(Duration.ofSeconds(1));
//             System.out.println("executing task1");
//             return "finished task1";
//         };
//         Callable<String> task2 = () -> {
//             Thread.sleep(Duration.ofSeconds(1));
//             System.out.println("executing task2");
//             return "finished task2";
//         };
//         Callable<String> task3 = () -> {
//             Thread.sleep(Duration.ofSeconds(1));
//             System.out.println("executing task3");
//             return "finished task3";
//         };
//         Callable<String> task4 = () -> {
//             Thread.sleep(Duration.ofSeconds(1));
//             System.out.println("executing task4");
//             return "finished task4";
//         };
//         Callable<String> task5 = () -> {
//             Thread.sleep(Duration.ofSeconds(1));
//             System.out.println("executing task5");
//             return "finished task5";
//         };
//         Callable<String> task6 = () -> {
//             Thread.sleep(Duration.ofSeconds(1));
//             System.out.println("executing task6");
//             return "finished task6";
//         };
//
//         Future<String> future1 = taskLimiter.submitOrThrow(task1);
//         Future<String> future2 = taskLimiter.submitOrThrow(task2);
//         Future<String> future3 = taskLimiter.submitOrThrow(task3);
//         Future<String> future4 = taskLimiter.submitOrThrow(task4);
//         Future<String> future5 = taskLimiter.submitOrThrow(task5);
//         Thread.sleep(Duration.ofMillis(1050));
//         Future<String> future6 = taskLimiter.submitOrThrow(task6);
//
//         assertEquals("finished task1", future1.get());
//         assertEquals("finished task2", future2.get());
//         assertEquals("finished task3", future3.get());
//         assertEquals("finished task4", future4.get());
//         assertEquals("finished task5", future5.get());
//         assertEquals("finished task6", future6.get());
//
//     }
//
//     @Test
//     void whenSubmitTwoTasksInPeriodAndTwoNotInPeriodThenRetry() throws ExecutionException, InterruptedException {
//         var executor = Executors.newVirtualThreadPerTaskExecutor();
//         var taskLimiter = new TaskLimiter(2, 1000, executor);
//         Callable<String> task1 = () -> {
//             System.out.println("executing task1");
//             return "finished task1";
//         };
//         Callable<String> task2 = () -> {
//             System.out.println("executing task2");
//             return "finished task2";
//         };
//         Callable<String> task3 = () -> {
//             System.out.println("executing task3");
//             return "finished task3";
//         };
//         Callable<String> task4 = () -> {
//             System.out.println("executing task4");
//             return "finished task4";
//         };
//
//         Future<String> future1 = taskLimiter.submitOrThrow(task1);
//         Future<String> future2 = taskLimiter.submitOrThrow(task2);
//         Thread.sleep(Duration.ofMillis(1000));
//         Future<String> future3 = taskLimiter.submitOrRetry(task3);
//         Future<String> future4 = taskLimiter.submitOrRetry(task4);
//
//         assertEquals("finished task1", future1.get());
//         assertEquals("finished task2", future2.get());
//         assertEquals("finished task3", future3.get());
//         assertEquals("finished task4", future4.get());
//
//     }
//
//     @Test
//     void whenSubmitFourTasksToBeExecutedSequentiallyThenOk() throws ExecutionException, InterruptedException {
//         var executor = Executors.newVirtualThreadPerTaskExecutor();
//         var taskLimiter = new TaskLimiter(1, 1000, executor);
//         Callable<String> task1 = () -> {
//             System.out.println("executing task1");
//             return "finished task1";
//         };
//         Callable<String> task2 = () -> {
//             System.out.println("executing task2");
//             return "finished task2";
//         };
//         Callable<String> task3 = () -> {
//             System.out.println("executing task3");
//             return "finished task3";
//         };
//         Callable<String> task4 = () -> {
//             System.out.println("executing task4");
//             return "finished task4";
//         };
//
//         Future<String> future1 = taskLimiter.submitOrRetry(task1);
//         Future<String> future2 = taskLimiter.submitOrRetry(task2);
//         Future<String> future3 = taskLimiter.submitOrRetry(task3);
//         Future<String> future4 = taskLimiter.submitOrRetry(task4);
//
//         assertEquals("finished task1", future1.get());
//         assertEquals("finished task2", future2.get());
//         assertEquals("finished task3", future3.get());
//         assertEquals("finished task4", future4.get());
//
//     }
//
//
//     @Test
//     void whenSubmitFourTasksToBeExecutedSequentiallyButOneCannotWaitThenOk() throws ExecutionException, InterruptedException {
//         var executor = Executors.newVirtualThreadPerTaskExecutor();
//         var taskLimiter = new TaskLimiter(1, 1000, executor);
//         Callable<String> task1 = () -> {
//             System.out.println("executing task1");
//             return "finished task1";
//         };
//         Callable<String> task2 = () -> {
//             System.out.println("executing task2");
//             return "finished task2";
//         };
//         Callable<String> task3 = () -> {
//             System.out.println("executing task3");
//             return "finished task3";
//         };
//         Callable<String> task4 = () -> {
//             System.out.println("executing task4");
//             return "finished task4";
//         };
//
//         Future<String> future1 = taskLimiter.submitOrRetry(task1);
//         Future<String> future2 = taskLimiter.submitOrRetry(task2);
//         Future<String> future3 = taskLimiter.submitOrRetry(task3);
//        
//         assertThrows(TooManyEventsInWindowException.class, () -> {
//             Future<String> future4 = taskLimiter.submitOrThrow(task4);
//         });
//         assertEquals("finished task1", future1.get());
//         assertEquals("finished task2", future2.get());
//         assertEquals("finished task3", future3.get());
//
//     }
//    
//     //
//     // @Test
//     // void test() throws InterruptedException {
//     //     // STEP 1: Instantiate
//     //     com.giuseppetavella.core.TaskLimiter taskLimiter = new com.giuseppetavella.core.TaskLimiter(5, 1000); // Max 5 tasks per 1000 milliseconds (1 second)
//     //
//     //     // STEP 2: Have a task ready  
//     //     Callable<String> task = () -> {
//     //         return "future result";
//     //     };
//     //
//     //     // STEP 3: Submit a task through the task limiter
//     //     // We can submit 5 tasks because that is the limit
//     //     Future<String> result1 = taskLimiter.submitOrThrow(task); // Try submitting a task. If rate overflow, custom exception is thrown 
//     //     Future<String> result2 = taskLimiter.submitOrThrow(task);
//     //     Future<String> result3 = taskLimiter.submitOrThrow(task);
//     //     Future<String> result4 = taskLimiter.submitOrThrow(task);
//     //     Future<String> result5 = taskLimiter.submitOrThrow(task);
//     //     Future<String> result6 = taskLimiter.submitOrThrow(task); // Exception is thrown here because rate overflow (6th task in same second)
//     //
//     // }
//    
// }