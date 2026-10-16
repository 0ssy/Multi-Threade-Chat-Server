package com.ChatPlatform.server;

import com.ChatPlatform.protocol.Message;
import com.ChatPlatform.protocol.MessageType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class Room {
    public enum Role { OWNER, MODERATOR, MEMBER }
    public final String name;
    public final String creator;
    public final long created = System.currentTimeMillis();
    public volatile String topic = "";
    public volatile int memberLimit = 100;
    public volatile boolean locked = false;
    public final boolean persistent;
    public final Map<String, Role> members = new ConcurrentSkipListMap<>(); // username -> role
    public final Set<String> banned = ConcurrentHashMap.newKeySet();
    public final Set<String> muted = ConcurrentHashMap.newKeySet();

    public Room(String name, String creator, boolean persistent) {
        this.name = name; this.creator = creator; this.persistent = persistent;
        this.members.put(creator, Role.OWNER);
    }

    public boolean addMember(String username, Role role){
        if (members.size() >= memberLimit && !members.containsKey(username)) return false;
        members.put(username, role);
        return true;
    }
    public boolean removeMember(String username){
        return members.remove(username) != null;
    }
    public boolean isMember(String username){ return members.containsKey(username); }
    public Role getRole(String username){ return members.get(username); }
    public boolean isOwner(String username){ return Role.OWNER.equals(getRole(username)); }
    public boolean isModerator(String username){
        Role r = getRole(username);
        return r == Role.OWNER || r == Role.MODERATOR;
    }
    public boolean canModerate(String actor, String target){
        if (!isModerator(actor)) return false;
        Role ar = getRole(actor), tr = getRole(target);
        if (tr == null) return false;
        if (ar == Role.OWNER) return true;
        return ar == Role.MODERATOR && tr != Role.OWNER;
    }
    public int memberCount(){ return members.size(); }

    public void broadcast(SessionManager sm, Message m, Session from){
        for (Map.Entry<String, Role> e : members.entrySet()) {
            if (e.getKey().equals(from.username)) continue;
            Session s = sm.byUsername(e.getKey());
            if (s != null) s.enqueue(m);
        }
    }
    public void broadcastIncluding(SessionManager sm, Message m){
        for (String u : members.keySet()) {
            Session s = sm.byUsername(u);
            if (s != null) s.enqueue(m);
        }
    }
}
