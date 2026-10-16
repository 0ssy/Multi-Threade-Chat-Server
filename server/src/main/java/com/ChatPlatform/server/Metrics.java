package com.ChatPlatform.server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Metrics {
    private final AtomicLong messages = new AtomicLong();
    private final AtomicLong dms = new AtomicLong();
    private final AtomicLong authFailures = new AtomicLong();
    private final AtomicLong drops = new AtomicLong();
    private final AtomicLong joins = new AtomicLong();
    private final AtomicLong leaves = new AtomicLong();
    private volatile int sessions = 0;
    private volatile int threads = 0;
    private volatile int queueDepth = 0;

    public void incMessages(){ messages.incrementAndGet(); }
    public void incDms(){ dms.incrementAndGet(); }
    public void incAuthFailures(){ authFailures.incrementAndGet(); }
    public void incDrops(){ drops.incrementAndGet(); }
    public void incJoins(){ joins.incrementAndGet(); }
    public void incLeaves(){ leaves.incrementAndGet(); }
    public void setSessions(int s){ this.sessions = s; }
    public void setThreads(int t){ this.threads = t; }
    public void setQueueDepth(int d){ this.queueDepth = d; }

    public long messages(){ return messages.get(); }
    public long dms(){ return dms.get(); }
    public long authFailures(){ return authFailures.get(); }
    public long drops(){ return drops.get(); }
    public long joins(){ return joins.get(); }
    public long leaves(){ return leaves.get(); }
    public int sessions(){ return sessions; }
    public int threads(){ return threads; }
    public int queueDepth(){ return queueDepth; }
}
