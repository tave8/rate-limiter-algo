package com.giuseppetavella.rate_limiter_algo.timeline;

import java.util.function.Function;

@FunctionalInterface
public interface BurstProtector extends Function<Timeline, Boolean> {
    
}