package com.ChatPlatform.server.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class FailedLoginTracker {
    private final int cap;
    private final long baseBackoffMs;
    private final Map<String, Long> failures = new ConcurrentHashMap<>();   // key -> last fail time
    private final Map<String, Integer> counts = new ConcurrentHashMap<>();   // key -> consecutive fails
    public FailedLoginTracker(int cap, long baseBackoffMs){ this.cap=cap; this.baseBackoffMs=baseBackoffMs; }
    public void recordFailure(String key){
        counts.merge(key, 1, Integer::sum);
        failures.put(key, System.currentTimeMillis());
    }
    public void reset(String key){
        counts.remove(key);
        failures.remove(key);
    }
    public boolean isBlocked(String key){
        Integer c = counts.get(key);
        if (c == null || c < cap) return false;
        Long last = failures.get(key);
        if (last == null) return false;
        long backoff = baseBackoffMs * (1L << Math.min(c - cap, 10));
        return System.currentTimeMillis() - last < backoff;
    }
    public int count(String key){
        Integer c = counts.get(key);
        return c == null ? 0 : c;
    }
}
