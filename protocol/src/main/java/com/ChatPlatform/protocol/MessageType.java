package com.ChatPlatform.protocol;

public enum MessageType {
    HELLO, AUTH, AUTH_OK, AUTH_FAIL,
    JOIN, JOINED, LEAVE, LEFT,
    MSG, DM,
    LIST_ROOMS, ROOMS, LIST_USERS, USERS,
    PING, PONG,
    ERROR, NOTICE,
    HISTORY,
    PRESENCE, TYPING,
    KICK, BAN, MUTE, TOPIC, LOCK,
    ADMIN
}
