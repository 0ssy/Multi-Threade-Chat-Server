package com.ChatPlatform.server.store;

import java.util.List;

public interface OfflineMessageStore {
    void save(String to, String from, String text, long id, long ts);
    List<String> takeOffline(String to);
}
