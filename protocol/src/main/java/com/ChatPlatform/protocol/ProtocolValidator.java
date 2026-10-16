package com.ChatPlatform.protocol;

import java.util.Map;
import java.util.regex.Pattern;

public final class ProtocolValidator {
    public static final int MAX_USERNAME = 32;
    public static final int MAX_ROOM = 32;
    public static final int MAX_MESSAGE = 4096;
    public static final int MAX_PASSWORD = 128;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{1,32}$");
    private static final Pattern ROOM_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{1,32}$");

    private ProtocolValidator() {}

    public static void requireField(Map<String,String> fields, String key) throws ProtocolException {
        String v = fields.get(key);
        if (v == null || v.isEmpty()) throw new ProtocolException("missing field: " + key);
    }

    public static String username(Map<String,String> fields) throws ProtocolException {
        requireField(fields, "username");
        String u = fields.get("username");
        if (!USERNAME_PATTERN.matcher(u).matches()) throw new ProtocolException("invalid username");
        return u;
    }

    public static String room(Map<String,String> fields) throws ProtocolException {
        requireField(fields, "room");
        String r = fields.get("room");
        if (!ROOM_PATTERN.matcher(r).matches()) throw new ProtocolException("invalid room");
        return r;
    }

    public static String text(Map<String,String> fields) throws ProtocolException {
        requireField(fields, "text");
        String t = fields.get("text");
        if (t.length() > MAX_MESSAGE) throw new ProtocolException("message too long");
        return t;
    }

    public static String password(Map<String,String> fields) throws ProtocolException {
        requireField(fields, "password");
        String p = fields.get("password");
        if (p.length() > MAX_PASSWORD) throw new ProtocolException("password too long");
        return p;
    }

    public static String version(Map<String,String> fields) throws ProtocolException {
        requireField(fields, "version");
        if (!"1".equals(fields.get("version"))) throw new ProtocolException("unsupported version");
        return "1";
    }

    public static class ProtocolException extends Exception {
        public ProtocolException(String msg){ super(msg); }
    }
}
