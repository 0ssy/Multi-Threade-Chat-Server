package com.ChatPlatform.protocol;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolValidatorTest {
    @Test
    void validUsername() throws Exception {
        Map<String,String> f = new HashMap<>();
        f.put("username", "alice_9");
        assertEquals("alice_9", ProtocolValidator.username(f));
    }
    @Test
    void invalidUsername() {
        Map<String,String> f = new HashMap<>();
        f.put("username", "a b");
        assertThrows(ProtocolValidator.ProtocolException.class, () -> ProtocolValidator.username(f));
    }
    @Test
    void usernameTooLong() {
        Map<String,String> f = new HashMap<>();
        f.put("username", "a".repeat(33));
        assertThrows(ProtocolValidator.ProtocolException.class, () -> ProtocolValidator.username(f));
    }
    @Test
    void validRoom() throws Exception {
        Map<String,String> f = new HashMap<>();
        f.put("room", "lobby");
        assertEquals("lobby", ProtocolValidator.room(f));
    }
    @Test
    void messageTooLong() {
        Map<String,String> f = new HashMap<>();
        f.put("text", "x".repeat(ProtocolValidator.MAX_MESSAGE + 1));
        assertThrows(ProtocolValidator.ProtocolException.class, () -> ProtocolValidator.text(f));
    }
    @Test
    void missingField() {
        assertThrows(ProtocolValidator.ProtocolException.class, () -> ProtocolValidator.username(Map.of()));
    }
    @Test
    void versionMismatch() {
        Map<String,String> f = new HashMap<>();
        f.put("version", "2");
        assertThrows(ProtocolValidator.ProtocolException.class, () -> ProtocolValidator.version(f));
    }
    @Test
    void versionOk() throws Exception {
        Map<String,String> f = new HashMap<>();
        f.put("version", "1");
        assertEquals("1", ProtocolValidator.version(f));
    }
}
