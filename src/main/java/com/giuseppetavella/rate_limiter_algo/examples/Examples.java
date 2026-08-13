package com.giuseppetavella.rate_limiter_algo.examples;

import com.giuseppetavella.rate_limiter_algo.RateLimiter;

/**
 * How to use the Rate Limiter in your project. 
 * These examples mostly assume you are using Spring, 
 */
class Examples {
    static void main(String[] args) {
        createRateLimiter();
        // associateRateLimiterToService();
    }

    /**
     * Create a Rate Limiter. You have two options: Create it with a custom concrete class 
     * or use the interface provided by the library. From the outside, there's no difference.
     */
    static void createRateLimiter() {
        // Imagine this is a configuration class in Spring
        var config = new Config();
        // Two options to get the beans from the configuration class.
        EmailRateLimiter emailLimiter = config.getEmailRateLimiter(); // Custom type (concrete class)
        RateLimiter aiLimiter = config.getAIRateLimiter(); // Library type (interface)
        
        
        // Example: Adding events to rate limiter
        for (int i = 0; i < 500; i++) {
            if(emailLimiter.add()) {
                System.out.println("added: " + i);
            } else {
                System.out.println("could not add "+i+" because: " + emailLimiter.getRejectionReason());
            }
            // Wait
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Associate a Rate Limiter to a Service.
     */
    static void associateRateLimiterToService() {
        // Imagine this is a configuration class in Spring
        var config = new Config();
        EmailRateLimiter emailLimiter = config.getEmailRateLimiter(); // Get the bean
        var emailService = new EmailService(emailLimiter); // Pass the rate limiter to the service 

        for (int i = 0; i < 500; i++) {
            if(emailLimiter.add()) {
                System.out.println("added: " + i);
            } else {
                System.out.println("could not add "+i+" because: " + emailLimiter.getRejectionReason());
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
}
