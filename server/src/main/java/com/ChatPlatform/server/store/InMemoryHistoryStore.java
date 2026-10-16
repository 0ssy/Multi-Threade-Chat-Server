package com.ChatPlatform.server.store;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryHistoryStore implements HistoryStore {
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> rooms = new ConcurrentHashMap<>();
    public void save(String room, String from, String text, long id, long ts){
        rooms.computeIfAbsent(room, k->new CopyOnWriteArrayList<>()).add(id+":"+from+":"+text+":"+ts);
    }
    public List<String> lastN(String room, int n){
        var l = rooms.getOrDefault(room, new CopyOnWriteArrayList<>());
        int from = Math.max(0, l.size()-n);
        return l.subList(from, l.size());
    }
    public List<String> before(String room, long beforeId, int n){
        var l = rooms.getOrDefault(room, new CopyOnWriteArrayList<>());
        java.util.List<String> out = new java.util.ArrayList<>();
        int count = 0;
        for (int i = l.size() - 1; i >= 0 && count < n; i--) {
            String entry = l.get(i);
            long id = Long.parseLong(entry.split(":", 2)[0]);
            if (id < beforeId) { out.add(entry); count++; }
        }
        java.util.Collections.reverse(out);
        return out;
    }
}