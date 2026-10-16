package com.ChatPlatform.server.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class H2UserStore implements UserStore {
    private final H2Database db;
    public H2UserStore(H2Database db) { this.db = db; }
    public boolean register(String username, String passwordHash) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, password_hash, created_ts) VALUES (?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setLong(3, System.currentTimeMillis());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            return false; // duplicate or constraint
        }
    }
    public String getHash(String username) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT password_hash FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) { return null; }
    }
    public boolean exists(String username) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }
}
