# Rate Limiter: Algorithm

This is the Algorithm component of my Rate Limiting Project, which also includes a microservice-like architecture where we battle-test the algorithm by simulating client making requests to different services. 

By rate limiting I mean the logic of limiting how many *events* are *added* during a time *window*.

This is the most generic definition I could come up with, here's what it implies: 
- An event can be anything, it can be a task, a request etc.
- Adding an event can signify submitting a task, executing a task, when the request is received etc. By design, the algorithm does not know or care about business-specific semantics, task or request lifecyle or things like that. Thus, "adding an event" is the most agnostic concept I could come up with, so you are not tied to use cases and can instead fit your own.
- Window, also known as time window, is the amount of time during which more than the maximum events that you define, cannot occur. More specifically, for a rate limiter, not more than the maximum events that you define can occur between the window start and now. A window is not the same as a period; A window is a fixed amount but it must be calculated from now. So the window start is `window_start = now - window`. On the other hand, you could calculate a period by multiplying some point in time with a factor. For example, multiplying something like `today_midnight + (period * 1)` gives you today at midnight plus the period. `today_midnight + (period * 2)` gives you today at midnight plus the the double of that period, and so on.

Two implementations for a rate limiter are offered: 

- History Queue. This was the first implementation, but it suffers from poor performance. At best, the complexity analysis is O(N) space and time, where N = number of max events, because at best max N events .  
- Timeline Manager.  


## Complexity analysis

Let: 
- E = number of max events
- T = number of timelines

| Implementation   | Dimension | Complexity | Motivation                                                                                 |
|:-----------------|:----------|:-----------|:-------------------------------------------------------------------------------------------|
| History Queue    | Space     | O(E)       | Stores every timestamp in the window; memory scales directly with total requests (N).      |
| History Queue    | Time      | O(E)       | Requires iterating over and pruning expired timestamp logs to calculate the current count. |
| Timeline Manager | Space     | O(1)       | Does not                                                                                   |




# Task Limiter

Limit the number of tasks that can be submitted in a given period (rate limiter).

For example, an email API can be sent max 5 emails per second.

- [See use case: 5 emails / second](#5-emails--second)

Notes:
- This implementation favors simplicity.
- This is an in-memory solution for simple use cases such as "max 10 tasks per second", "max 50 tasks per minute".
- It does not survive power loss (it's not persistent).
- It is not suitable for a large amount of tasks or sub-millisecond precision.
- However it works fine if you don't care about millisecond precision, task amount is reasonable (0-1000) and in general non-mission-critical operations.


## Get started

1. Clone all repositories into the same directory
2. Open a terminal in that directory
3. Run the python script `python server_automation.py`.

If you don't change anything manually, this is all you need to do.

Note: The processes currently open at the ports that are intended for usage in this project, will be killed. Running the command builds each server and runs it; It does not automatically update the urls/ports for each subproject. So if you change ports, you also have to update the urls/ports manually, where relevant. Fortunately, it's very easy; Just go in the `resources` directory for the *client* and *rate limiter*, and update the json that you see in there, with the new url/port.



## Usage


You can subclass `com.giuseppetavella.core.TaskLimiter` to fit your use cases.

The limits are applied to the com.giuseppetavella.core.TaskLimiter object, so tasks that need to be rate-limited must go through the same instance, because the instance contains the history of task submissions.

### Simple usage

```java
import com.giuseppetavella.core.TaskLimiter;

// STEP 1: Instantiate
TaskLimiter taskLimiter = new TaskLimiter(5, 1000); // Max 5 tasks per 1000 milliseconds (1 second)

        // STEP 2: Have a task ready  
        Callable<String> task = () -> {
          return "future result";
        };

        // STEP 3: Submit a task through the task limiter
// We can submit 5 tasks because that is the limit
        Future<String> result1 = taskLimiter.submitOrThrow(task); // Try submitting a task. If rate overflow, custom exception is thrown 
        Future<String> result2 = taskLimiter.submitOrThrow(task);
        Future<String> result3 = taskLimiter.submitOrThrow(task);
        Future<String> result4 = taskLimiter.submitOrThrow(task);
        Future<String> result5 = taskLimiter.submitOrThrow(task);
        Future<String> result6 = taskLimiter.submitOrThrow(task); // Exception is thrown here because rate overflow (6th task in same second)

```

### Advanced usage

```java
import com.giuseppetavella.core.TaskLimiter;

// Upon instantation, you can optionally pass a custom thread pool. 
// If you do not, one will be created for you.
ExecutionService executor = Executors.newVirtualThreadPerTaskExecutor();

        TaskLimiter taskLimiter = new TaskLimiter(5, 1000, executor); // Max 5 tasks per 1000 milliseconds (1 second)

```


## Use cases

### 5 emails / second

```java

import com.giuseppetavella.EmailLimiter;

com.giuseppetavella.core.TaskLimiter emailLimiter = new EmailLimiter(5, 1); // Max 5 emails per second

// // STEP 2: Have a task ready  
// Callable<String> emailTask = () -> {
//     Thread.sleep(Duration.ofMillis(1000)); // 
//     return "future result";
// };
//
// // STEP 3: Submit a task through the task limiter
// // We can submit 5 tasks because that is the limit
// Future<String> result1 = taskLimiter.submitOrThrow(task); // Try submitting a task. If rate overflow, custom exception is thrown 
```


## Build & Docker

Run both containers (rate limiter and rate limiter server):

```
docker run -p 9000:9000 -d rate-limiter

docker run -p 9100:9100 -d rate-limiter-server

```

To change the output jar, add this to your pom.xml (inside the "build" tag):

```xml
<build>
    <finalName>app</finalName>
</build>

```


Then:

Every time you make a change to the project, you need to build into a jar, like so
(Make sure to  use the --build flag for Docker Compose; if you use just click on Docker Compose from the IDE,
it's likely you won't see the updated project, because Docker will has cached the image. With --build, you force
to rebuild the image.):

`
./mvnw clean package -DskipTests

docker compose up --build
`

1. Have Dockerfile ready
2. Have Docker Compose file ready
3. Make sure Docker daemon is running
4. Build the image: `docker build -t rate-limiter .`
5. Run the image manually, to make sure container and port mapping are ok. Command: `docker run -p 9000:9000 rate-limiter`
6. Run the Docker Compose, so you can start all containers/services at once. Command: `docker compose up --build`


## Reasoning & Challenges

### Defining the real problem

At first I thought that the solution consisted in splitting the time into periods, and have something continuously update the "current start of period".

If I know the current start of period and the time now, then I could keep track of how many tasks have been submitted in this time delta.

So something had to keep the the "current start of period" updated. This something was a thread that would wake up at a custom interval and would keep the "current start of period" in sync.

So let's say you start the task limiter now to rate limit 5 tasks per second, then a thread wakes up every second and keeps updating the "current start of period"

which is simply `startOfPeriod += period`.

This solution was not viable for at least these reasons:
- Waste of resources. Waking up a thread just to keep the "current start of period" in sync. CPU cycles wasted and a OS context switch just to keep
  the current start of period updated. If the tasks to be rate limited are infrequent, resources are wasted anyways.
- Not scalable, not secure and not performant. A "time updater thread per task limiter" model as well as a thread waking up at a custom interval (such each second) suffers from trust issues,
  in the sense that the user directly controls how frequently the thread wakes up.
- Reliance on OS hoping that context switch would occur timely and without much delay. Uncertainty about how to deal with task submission if task submission
  occurred in the the time it takes between the actual end of the current period and when the thread wakes up to update the current period.

**But most importantly, that wasn't even what the problem was all about!**

I thought the problem was splitting the time into chunks, then I asked myself, why am I structuring time? It feels like I'm inventing time.

I'm wasting resources just to keep track of what is the current start of period, but don't I already know what second is now and what is the previous second?

So I thought, maybe I can ground the original start time to be a floored number, for example instead of taking 12h:32m:34s, let's just start from 12h:32m:00s.


This approach entails that in an 1 second "artificial" period, I can have 5 tasks submitted. Then this current artificial start of period gets updated,

and immediately after that a task is submitted. The time difference between this newly submitted task and the last submitted task is only 5 milliseconds away.

And altogether, the 6 tasks have been submitted in less than a second. So I realized, this cannot happen, I need to reformulate the problem as follows:

```
INITIAL PROBLEM: allow max N tasks in T period

REFORMULATED PROBLEM: the sum of the time deltas of the most recent N tasks is <= T period,
    where time delta = time of new task submission - time of last task submission
```

But this approach forced me to think about time deltas, keeping track of them etc.

So I said myself, okay this makes more sense, but what do I actually need for the problem? The number of tasks or the deltas?

And eventually realized that the tasks count in the T period was what I actually needed.

This means that the problem was not about structuring time or even knowing a current start of period.

The problem revolved around how many tasks have been submitted in the T period, regardless of whether that period is part of a "time structure" like

the difference between this second and the previous second, etc.

What matters is knowing whether the last N tasks have been submitted in the T period, which is another way of stating the most up-to-date reformulation of the problem:

`the number of tasks submitted between now and now - T period must be <= N max tasks`

### Waiting without waiting

In life, either you change yourself or you change the environment (or maybe there's nothing to change).

The problem is that waiting for tests to finish is not ideal.

A task limiter should limit the number of tasks in a given time window.

However that shouldn't mean waiting real time just to test that it works.

So back to our metaphor; Either you make the thread wait (and you wait for it), or you simulate the waiting.

Either you wait real time just so time can move forward, or you make that time move forward yourself.

It's on this intuition that a solution was created to simulate waiting without actually waiting.

Each history queue has its own concept of time and can be easily modified.




int maxEvents = 100;
        long window = 1000;
        
        RateLimiter rateLimiter1 = new TimelineManager(maxEvents, window);
        RateLimiter rateLimiter2 = new HistoryQueue(maxEvents, window);


### Timeline

The Rate Limiter implementation gives up determinism and loosens up events count accuracy, to gain in efficiency, speed and scalability.

Rate limiting accuracy, which comes down to events count accuracy in the time window, depends on the number of timelines, which is configurable. It is also affected by thread context switching. 

The number of timelines is, effectively, like a CPU clock. You can also see it as a heartbeat frequency; The faster it beats, the quicker, more likely and more precisely you'll know and detect what just happened since last heartbeat. The heartbeat beats on average at `window * nTimelines` speed, which is why increasing the number of timelines increases the heartbeat speed (up to a point), which in turn increases events count accuracy, which means detecting events overflow faster.

It's not always possible to have instant feedback on whether the max events have been achieved in the window at the exact moment the overflow has been reached.

By loosening up on instant reactivity and accuracy, we achieve great efficiency, scalability and speed: The algorithm has constant space complexity on all operations and O(K) time complexity per new request, where K = number of timelines, which we have control over, is very low (1-5) and known upfront. 

Simply put, it is possible that very quick burts of requests not be detected, if they occur in less than the time window time and the timing is such that no timeline was awakened to reset its events count and check if any events overflow occurred. 



