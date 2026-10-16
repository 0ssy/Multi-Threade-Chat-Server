package com.ChatPlatform.server.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    public int port = 5000;
    public int maxFrame = 1 << 20;
    public int idleTimeoutMs = 60000;
    public int maxConnections = 1024;
    public int maxConnectionsPerIP = 16;
    public int historyLimit = 50;
    public int typingThrottleMs = 1500;
    public int rateLimitCapacity = 30;
    public long rateLimitRefillMs = 1000;
    public int failedLoginCap = 5;
    public long failedLoginBackoffBaseMs = 1000;
    public StoreType storeType = StoreType.MEMORY;
    public String dbPath = "data/chatdb";
    public boolean tlsEnabled = false;
    public String tlsKeystore = "keystore.jks";
    public String tlsKeystorePassword = "changeit";

    public enum StoreType { MEMORY, H2 }

    public static Config load() { return load(null); }

    public static Config load(String path) {
        Config c = new Config();
        if (path != null) {
            try (FileInputStream fis = new FileInputStream(path)) {
                Properties p = new Properties();
                p.load(fis);
                c.port = parseInt(p, "port", c.port);
                c.maxConnections = parseInt(p, "max.connections", c.maxConnections);
                c.maxConnectionsPerIP = parseInt(p, "max.connections.per.ip", c.maxConnectionsPerIP);
                c.historyLimit = parseInt(p, "history.limit", c.historyLimit);
                c.typingThrottleMs = parseInt(p, "typing.throttle.ms", c.typingThrottleMs);
                c.rateLimitCapacity = parseInt(p, "rate.limit.capacity", c.rateLimitCapacity);
                c.failedLoginCap = parseInt(p, "failed.login.cap", c.failedLoginCap);
                c.storeType = StoreType.valueOf(p.getProperty("store.type", c.storeType.name()).toUpperCase());
                c.dbPath = p.getProperty("db.path", c.dbPath);
                c.tlsEnabled = Boolean.parseBoolean(p.getProperty("tls.enabled", String.valueOf(c.tlsEnabled)));
            } catch (IOException ignored) { /* use defaults */ }
        }
        return c;
    }

    private static int parseInt(Properties p, String key, int def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }
}
