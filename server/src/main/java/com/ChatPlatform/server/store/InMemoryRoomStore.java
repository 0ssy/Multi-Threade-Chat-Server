package com.ChatPlatform.server.store;

import java.util.HashMap;
import java.util.Map;

public class InMemoryRoomStore implements RoomStore {
    private final Map<String, Map<String,String>> roomRoles = new HashMap<>();
    private final Map<String, Map<String,String>> bans = new HashMap<>();
    private final Map<String, Map<String,String>> mutes = new HashMap<>();
    public void upsertRoom(String name, String owner, String topic, boolean locked, boolean persistent) {}
    public void removeRoom(String name) {}
    public void setMemberRole(String room, String user, String role){
        roomRoles.computeIfAbsent(room, k->new HashMap<>()).put(user, role);
    }
    public void removeMember(String room, String user){
        var r = roomRoles.get(room);
        if (r != null) r.remove(user);
    }
    public void banUser(String room, String user){
        bans.computeIfAbsent(room, k->new HashMap<>()).put(user, "1");
    }
    public void unbanUser(String room, String user){
        var r = bans.get(room);
        if (r != null) r.remove(user);
    }
    public void muteUser(String room, String user){
        mutes.computeIfAbsent(room, k->new HashMap<>()).put(user, "1");
    }
    public void unmuteUser(String room, String user){
        var r = mutes.get(room);
        if (r != null) r.remove(user);
    }
    public Map<String,String> roomRoles(String room){
        var r = roomRoles.get(room);
        return r == null ? Map.of() : new HashMap<>(r);
    }
    public boolean isBanned(String room, String user){
        var r = bans.get(room);
        return r != null && r.containsKey(user);
    }
    public boolean isMuted(String room, String user){
        var r = mutes.get(room);
        return r != null && r.containsKey(user);
    }
}
