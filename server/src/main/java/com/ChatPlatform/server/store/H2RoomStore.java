package com.ChatPlatform.server.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class H2RoomStore implements RoomStore {
    private final H2Database db;
    public H2RoomStore(H2Database db) { this.db = db; }
    public void upsertRoom(String name, String owner, String topic, boolean locked, boolean persistent) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("MERGE INTO rooms (name, owner, topic, locked, persistent, created_ts) KEY (name) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, name);
            ps.setString(2, owner);
            ps.setString(3, topic);
            ps.setBoolean(4, locked);
            ps.setBoolean(5, persistent);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) { /* ignore */ }
    }
    public void removeRoom(String name) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM rooms WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) { /* ignore */ }
    }
    public void setMemberRole(String room, String user, String role) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("MERGE INTO room_members (room, user, role) KEY (room, user) VALUES (?,?,?)")) {
            ps.setString(1, room);
            ps.setString(2, user);
            ps.setString(3, role);
            ps.executeUpdate();
        } catch (SQLException e) { /* ignore */ }
    }
    public void removeMember(String room, String user) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM room_members WHERE room = ? AND user = ?")) {
            ps.setString(1, room);
            ps.setString(2, user);
            ps.executeUpdate();
        } catch (SQLException e) { /* ignore */ }
    }
    public void banUser(String room, String user) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("MERGE INTO room_bans (room, user) KEY (room, user) VALUES (?,?)")) {
            ps.setString(1, room);
            ps.setString(2, user);
            ps.executeUpdate();
        } catch (SQLException e) { /* ignore */ }
    }
    public void unbanUser(String room, String user) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM room_bans WHERE room = ? AND user = ?")) {
            ps.setString(1, room);
            ps.setString(2, user);
            ps.executeUpdate();
        } catch (SQLException e) { /* ignore */ }
    }
    public void muteUser(String room, String user) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("MERGE INTO room_mutes (room, user) KEY (room, user) VALUES (?,?)")) {
            ps.setString(1, room);
            ps.setString(2, user);
            ps.executeUpdate();
        } catch (SQLException e) { /* ignore */ }
    }
    public void unmuteUser(String room, String user) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM room_mutes WHERE room = ? AND user = ?")) {
            ps.setString(1, room);
            ps.setString(2, user);
            ps.executeUpdate();
        } catch (SQLException e) { /* ignore */ }
    }
    public Map<String,String> roomRoles(String room) {
        Map<String,String> roles = new HashMap<>();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT user, role FROM room_members WHERE room = ?")) {
            ps.setString(1, room);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) roles.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) { return roles; }
        return roles;
    }
    public boolean isBanned(String room, String user) {
        return exists(room, "room_bans", user);
    }
    public boolean isMuted(String room, String user) {
        return exists(room, "room_mutes", user);
    }
    private boolean exists(String room, String table, String user) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM " + table + " WHERE room = ? AND user = ?")) {
            ps.setString(1, room);
            ps.setString(2, user);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }
}
