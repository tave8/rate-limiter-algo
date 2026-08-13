package com.giuseppetavella.rate_limiter_algo.examples;

public class EmailService {
    
    private final EmailRateLimiter limiter;

    /**
     * In Spring, dependency is automatically injected only if 
     * have exactly one instance of {@code EmailRateLimiter}, which is why
     * it's more convenient to have your own class. 
     * 
     * @param limiter the service-bound rate limiter. 
     *                1 rate limiter instance per 1 service instance.
     */
    public EmailService(EmailRateLimiter limiter) {
        this.limiter = limiter;
    }

    /**
     * Some operation hitting the actual API.
     * 
     * @return
     */
    public String sendEmail() {
        
        // Apply rate limit
        if( !limiter.add() ) {
            return "rate limit surpassed because " + limiter.getRejectionReason();
        }
        
        return "email sent (after calling actual API...)";
        
    }


    /**
     * Some operation hitting the actual API.
     *
     * @return
     */
    public String sendEmailWithAttachments() {

        // Apply rate limit
        if( !limiter.add() ) {
            return "rate limit surpassed because " + limiter.getRejectionReason();
        }

        return "email sent with attachments (after calling real API...)";

    }
    
}
