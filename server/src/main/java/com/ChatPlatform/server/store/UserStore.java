package com.ChatPlatform.server.store;

public interface UserStore {
    boolean register(String username, String passwordHash);
    String getHash(String username);
    boolean exists(String username);
}