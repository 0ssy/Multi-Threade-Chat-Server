package com.ChatPlatform.protocol;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record Message(MessageType type, Map<String,String> fields) {
    public Message {
        if (fields == null) {
            fields = Collections.emptyMap();
        }
    }
    public String get(String key){ return fields.get(key); }
    public static Message of(MessageType type, String key, String value){
        Map<String,String> f = new HashMap<>(1);
        f.put(key, value);
        return new Message(type, f);
    }
    public static Message of(MessageType type, Map<String,String> fields){
        return new Message(type, fields);
    }
}
