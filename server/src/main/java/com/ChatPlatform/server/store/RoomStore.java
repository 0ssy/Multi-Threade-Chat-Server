package com.ChatPlatform.server.store;

public interface RoomStore {
    void upsertRoom(String name, String owner, String topic, boolean locked, boolean persistent);
    void removeRoom(String name);
    void setMemberRole(String room, String user, String role);
    void removeMember(String room, String user);
    void banUser(String room, String user);
    void unbanUser(String room, String user);
    void muteUser(String room, String user);
    void unmuteUser(String room, String user);
    java.util.Map<String,String> roomRoles(String room);
    boolean isBanned(String room, String user);
    boolean isMuted(String room, String user);
}
