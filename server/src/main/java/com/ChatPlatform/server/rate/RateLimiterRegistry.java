package com.ChatPlatform.server.rate;

import com.ChatPlatform.server.rate.TokenBucketRateLimiter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiterRegistry {
    private final Map<String, TokenBucketRateLimiter> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final long refillMs;
    public RateLimiterRegistry(int capacity, long refillMs){ this.capacity=capacity; this.refillMs=refillMs; }
    public boolean tryAcquire(String key){
        TokenBucketRateLimiter b = buckets.computeIfAbsent(key, k -> new TokenBucketRateLimiter(capacity, refillMs));
        return b.tryAcquire();
    }
    public int size(){ return buckets.size(); }
}
