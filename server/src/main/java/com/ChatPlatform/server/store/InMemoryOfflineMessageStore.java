package com.ChatPlatform.server.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryOfflineMessageStore implements OfflineMessageStore {
    private final Map<String, CopyOnWriteArrayList<String>> queues = new ConcurrentHashMap<>();
    public void save(String to, String from, String text, long id, long ts){
        queues.computeIfAbsent(to, k->new CopyOnWriteArrayList<>()).add(id+":"+from+":"+text+":"+ts);
    }
    public List<String> takeOffline(String to){
        var q = queues.remove(to);
        return q == null ? new ArrayList<>() : new ArrayList<>(q);
    }
}
