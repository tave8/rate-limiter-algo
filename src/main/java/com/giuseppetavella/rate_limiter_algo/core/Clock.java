package com.giuseppetavella.rate_limiter_algo.core;

/**
 * A Clock interface defines the contract for a Clock,
 * whose goal is to decouple time logic from rate limiting logic.
 * The core idea behind this decoupling is that a Rate Limiter has a clock, 
 * this clock has methods, and a Rate Limiter only wants 
 * to get the now regardless of what that means. The now logic 
 * should be the clock's responsability, not the rate limiter's.
 * 
 * <br><br>
 * For simplicity, the time unit is always milliseconds.
 * 
 * @author Giuseppe Tavella
 */
public interface Clock {
    /**
     * Add artificial time. In other words, make time pass logically, 
     * without having to wait for physical time.
     * 
     * @param delay the amount of artificial time to add to the cumulative delay
     */
    Clock after(long delay);

    /**
     * The cumulative delay is the artificial time that was added 
     * in the implementation. As the name suggests, it is <i>cumulative</i>
     * because it accumulates the delays.
     * 
     */
    long getCumulativeDelay();

    /**
     * Use this method when you want the <b><i>physical now</i></b>.
     * The actual now, as the name suggests, is the actual physical now,
     * regardless of whatever mechanism the implementation uses to determine it.
     * For example, in a computer there are different ways 
     * for determining the now. All we care about is consistency.
     * All we care is that this is the actual physical now as 
     * determined by whatever <i>consistent</i> mechanism 
     * the implementation uses to determine it.
     * 
     * @return
     */
    long getActualNow();

    /**
     * Use this method when you want the <b><i>artificial now</i></b>.
     * To be more precise, when you don't care about what now means, 
     * only that it consistently represents the now. Of course,
     * the internal now can correspond to the physical now, but this
     * is abstracted away from the caller.
     * 
     * You may also call it the <i>internal now</i>, in the sense that 
     * it's local to the implementation. The naming promotes not having
     * to make the effort to distinguish physical from articial time.
     * 
     * @return
     */
    long getNow();
    
    long measureElapsed();
}
