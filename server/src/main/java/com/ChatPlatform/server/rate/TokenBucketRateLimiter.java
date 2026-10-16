package com.ChatPlatform.server.rate;

public class TokenBucketRateLimiter {
    private final int capacity;
    private final long refillMs;
    private long tokens;
    private long lastRefill;

    public TokenBucketRateLimiter(int capacity, long refillMs){
        this.capacity=capacity; this.refillMs=refillMs;
        this.tokens=capacity; this.lastRefill=System.currentTimeMillis();
    }
    public synchronized boolean tryAcquire(){
        long now=System.currentTimeMillis();
        long refill = (now-lastRefill)/refillMs;
        if (refill>0){ tokens=Math.min(capacity, tokens+refill); lastRefill=now; }
        if (tokens>0){ tokens--; return true; }
        return false;
    }
}