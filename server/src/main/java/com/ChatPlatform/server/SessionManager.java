package com.ChatPlatform.server;

import com.ChatPlatform.protocol.Message;
import com.ChatPlatform.protocol.MessageType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Session> byUser = new ConcurrentHashMap<>();

    public void register(Session s){ sessions.put(s.id, s); }
    public void unregister(Session s){
        sessions.remove(s.id);
        if (s.username != null) byUser.remove(s.username, s);
    }
    public Session get(long id){ return sessions.get(id); }
    public Session byUsername(String u){ return byUser.get(u); }

    public Session bindUser(Session s, String username){
        Session old = byUser.put(username, s);
        s.username = username;
        if (old != null && old.id != s.id) {
            old.enqueue(new Message(MessageType.ERROR, Map.of("code","8","msg","duplicate login kicked")));
            old.close();
        }
        return old;
    }
    public void unbind(Session s){
        if (s.username != null) byUser.remove(s.username, s);
        s.username = null;
    }
    public Collection<Session> all(){ return new ArrayList<>(sessions.values()); }
    public int count(){ return sessions.size(); }
}
