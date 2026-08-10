package com.giuseppetavella.rate_limiter_algo.timeline;
 

/**
 * Settings for burst protection for the Timeline Manager Rate Limiter implementation.
 * You can configure percentage thresholds such that, once surpassed,
 * can trigger preventive, premature back-off, instead of waiting 
 * to get to 100% max events in the window.
 * 
 * <br><br>
 * 
 * For example, you can configure a Burst Protection so that,
 * upon achieving 90% of the time window and with 95% of the max events allowed 
 * already added during this window, rejects new events.
 * 
 * <br><br>
 * 
 * How you reject those events or set up this logic is up to you; 
 * This class only externalizes burst protection logic.
 * 
 * <br><br>
 * 
 * To truly be used as burst protection, the <code>eventThreshold</code> should be higher than the <code>windowThreshold</code>,
 * so as to mean "the percentage of events added is higher than the percentage progress in this time window". 
 * This mechanism works in the opposite direction as well; We could also express that 
 * "Not enough events were added in the given window percentage."
 * 
 * <br><br> 
 * 
 * Note: 
 * <ul>
 *     <li>By "threshold" it is mean a percentage threshold, scale 0-1</li>
 * </ul>
 * 
 * 
 */
public class BurstProtection {
    private boolean enabled;
    private final double eventThreshold;
    private final double windowThreshold;
    private final int maxEvents;
    private final long window;
    
    public BurstProtection(double eventThreshold,
                           double windowThreshold,
                           int maxEvents,
                           long window) 
    {
        this.eventThreshold = eventThreshold;
        this.windowThreshold = windowThreshold;
        this.maxEvents = maxEvents;
        this.window = window;
        this.enabled = true;
    }

    
    /**
     * Disable Burst Protection. Cannot re-enable it.
     * @return
     */
    public BurstProtection notEnabled() {
        if(!enabled) {
            throw new RuntimeException("Burst Protection was already disabled, cannot re-disable it.");
        }
        this.enabled = false;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasReachedEventThreshold(long eventCount) {
        double p = (double) eventCount / maxEvents; 
        return p >= eventThreshold;
    }

    public boolean hasReachedWindowThreshold(long now, long windowStart) {
        double p = (double) (now - windowStart) / window;
        return p >= windowThreshold;
    }


    /**
     * Normal Burst Protection:
     * <ul>
     *     <li><b>Event threshold</b>: 95%</li>
     *     <li><b>Window threshold</b>: 90%</li>
     * </ul>
     * 
     * It means: When 90% of the window is surpassed AND 
     * more than 95% of max events have already been added, reject new events.
     * 
     * @param maxEvents 
     * @param window
     * @return
     */
    public static BurstProtection buildNormal(int maxEvents, long window) {
        return new BurstProtection(.95, .9, maxEvents, window);
    }
    
}
    