package com.ChatPlatform.server.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class H2OfflineMessageStore implements OfflineMessageStore {
    private final H2Database db;
    public H2OfflineMessageStore(H2Database db) { this.db = db; }
    public void save(String to, String from, String text, long id, long ts) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO offline_messages (id, to_user, from_user, text, ts) VALUES (?,?,?,?,?)")) {
            ps.setLong(1, id);
            ps.setString(2, to);
            ps.setString(3, from);
            ps.setString(4, text);
            ps.setLong(5, ts);
            ps.executeUpdate();
        } catch (SQLException e) { /* ignore */ }
    }
    public List<String> takeOffline(String to) {
        List<String> out = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        try (Connection c = db.connect()) {
            try (PreparedStatement sel = c.prepareStatement("SELECT id, from_user, text, ts FROM offline_messages WHERE to_user = ? ORDER BY id ASC")) {
                sel.setString(1, to);
                try (ResultSet rs = sel.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getLong(1) + ":" + rs.getString(2) + ":" + rs.getString(3) + ":" + rs.getLong(4));
                        ids.add(rs.getLong(1));
                    }
                }
            }
            if (!ids.isEmpty()) {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM offline_messages WHERE to_user = ? AND id <= ?")) {
                    del.setString(1, to);
                    del.setLong(2, ids.get(ids.size() - 1));
                    del.executeUpdate();
                }
            }
        } catch (SQLException e) { return out; }
        return out;
    }
}
