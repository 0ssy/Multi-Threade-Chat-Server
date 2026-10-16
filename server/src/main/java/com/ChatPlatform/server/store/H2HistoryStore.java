package com.ChatPlatform.server.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class H2HistoryStore implements HistoryStore {
    private final H2Database db;
    public H2HistoryStore(H2Database db) { this.db = db; }
    public void save(String room, String from, String text, long id, long ts) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO messages (id, room, from_user, text, ts) VALUES (?,?,?,?,?)")) {
            ps.setLong(1, id);
            ps.setString(2, room);
            ps.setString(3, from);
            ps.setString(4, text);
            ps.setLong(5, ts);
            ps.executeUpdate();
        } catch (SQLException e) { /* ignore duplicate id */ }
    }
    public List<String> lastN(String room, int n) {
        List<String> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT id, from_user, text, ts FROM messages WHERE room = ? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, room);
            ps.setInt(2, n);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getLong(1) + ":" + rs.getString(2) + ":" + rs.getString(3) + ":" + rs.getLong(4));
            }
        } catch (SQLException e) { return out; }
        java.util.Collections.reverse(out);
        return out;
    }
    public List<String> before(String room, long beforeId, int n) {
        List<String> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT id, from_user, text, ts FROM messages WHERE room = ? AND id < ? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, room);
            ps.setLong(2, beforeId);
            ps.setInt(3, n);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getLong(1) + ":" + rs.getString(2) + ":" + rs.getString(3) + ":" + rs.getLong(4));
            }
        } catch (SQLException e) { return out; }
        java.util.Collections.reverse(out);
        return out;
    }
}
