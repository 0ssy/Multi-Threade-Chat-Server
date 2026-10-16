package com.ChatPlatform.server.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUserStore implements UserStore {
    private final Map<String,String> data = new ConcurrentHashMap<>();
    public boolean register(String u, String h){ return data.putIfAbsent(u,h)==null; }
    public String getHash(String u){ return data.get(u); }
    public boolean exists(String u){ return data.containsKey(u); }
}