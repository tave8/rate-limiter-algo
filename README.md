### Timeline

The Timeline implementation is a non-deterministic algorithm for Rate Limiting. It gives up determinism and loosens up events count accuracy, to gain in efficiency, speed and scalability.

Rate limiting accuracy, which comes down to events count accuracy in the time window, depends on the number of timelines, which is configurable. It is also affected by thread context switching. 

The number of timelines is, effectively, like a CPU clock. You can also see it as a heartbeat frequency; The faster it beats, the quicker, more likely and more precisely you'll know and detect what just happened since last heartbeat. The heartbeat beats on average at `window * nTimelines` speed, which is why increasing the number of timelines increases the heartbeat speed (up to a point), which in turn increases events count accuracy, which means detecting events overflow faster.

It's not always possible to have instant feedback on whether the max events have been achieved in the window at the exact moment the overflow has been reached.

By loosening up on instant reactivity and accuracy, we achieve great efficiency, scalability and speed: The algorithm has constant space complexity on all operations and O(K) time complexity per new request, where K = number of timelines, which we have control over, is very low (1-5) and known upfront. 

Simply put, it is possible that very quick burts of requests not be detected, if they occur in less than the time window time and the timing is such that no timeline was awakened to reset its events count and check if any events overflow occurred. 



