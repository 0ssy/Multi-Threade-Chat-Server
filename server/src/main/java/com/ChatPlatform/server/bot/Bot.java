package com.ChatPlatform.server.bot;

import com.ChatPlatform.server.SessionManager;

public interface Bot {
    String name();
    void onMessage(String room, String from, String text, SessionManager sm);
}