package com.ChatPlatform.server.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class H2Database {
    private final String url;

    public H2Database(String jdbcUrl) {
        this.url = jdbcUrl;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public void init() throws SQLException {
        try (Connection c = connect()) {
            c.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS users (username VARCHAR(32) PRIMARY KEY, password_hash VARCHAR(255) NOT NULL, created_ts BIGINT NOT NULL)"
            );
            c.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS rooms (name VARCHAR(32) PRIMARY KEY, owner VARCHAR(32) NOT NULL, topic VARCHAR(256), locked BOOLEAN NOT NULL DEFAULT FALSE, persistent BOOLEAN NOT NULL DEFAULT FALSE, created_ts BIGINT NOT NULL)"
            );
            c.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS room_members (room VARCHAR(32) NOT NULL, user VARCHAR(32) NOT NULL, role VARCHAR(16) NOT NULL DEFAULT 'MEMBER', PRIMARY KEY (room, user))"
            );
            c.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS room_bans (room VARCHAR(32) NOT NULL, user VARCHAR(32) NOT NULL, PRIMARY KEY (room, user))"
            );
            c.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS room_mutes (room VARCHAR(32) NOT NULL, user VARCHAR(32) NOT NULL, PRIMARY KEY (room, user))"
            );
            c.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS messages (id BIGINT PRIMARY KEY, room VARCHAR(32) NOT NULL, from_user VARCHAR(32) NOT NULL, text VARCHAR(4096) NOT NULL, ts BIGINT NOT NULL)"
            );
            c.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS offline_messages (id BIGINT PRIMARY KEY, to_user VARCHAR(32) NOT NULL, from_user VARCHAR(32) NOT NULL, text VARCHAR(4096) NOT NULL, ts BIGINT NOT NULL)"
            );
            c.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_messages_room ON messages(room, id)");
            c.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_offline_to ON offline_messages(to_user)");
        }
    }
}
