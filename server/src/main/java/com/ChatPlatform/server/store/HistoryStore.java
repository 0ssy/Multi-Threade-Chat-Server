package com.ChatPlatform.server.store;

import java.util.List;

public interface HistoryStore {
    void save(String room, String from, String text, long id, long ts);
    List<String> lastN(String room, int n);
    List<String> before(String room, long beforeId, int n);
}