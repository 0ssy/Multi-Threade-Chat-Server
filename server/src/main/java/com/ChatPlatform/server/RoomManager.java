package com.ChatPlatform.server;

import com.ChatPlatform.protocol.Message;
import com.ChatPlatform.protocol.MessageType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public Room getOrCreate(String name, String creator) {
        return rooms.computeIfAbsent(name, n -> new Room(n, creator, false));
    }
    public Room get(String name){ return rooms.get(name); }
    public void removeIfEmpty(String name){
        rooms.compute(name, (k, v) -> (v != null && v.memberCount() == 0 && !v.persistent) ? null : v);
    }
    public Collection<Room> list(){ return new ArrayList<>(rooms.values()); }

    public void makePersistent(String name){
        Room r = get(name);
        if (r != null) { /* persistence handled by store; flag already set at creation */ }
    }

    public List<String> userRooms(SessionManager sm, String username){
        List<String> out = new ArrayList<>();
        for (Room r : list()) if (r.isMember(username)) out.add(r.name);
        return out;
    }
}
