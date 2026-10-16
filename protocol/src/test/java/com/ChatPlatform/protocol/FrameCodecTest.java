package com.ChatPlatform.protocol;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FrameCodecTest {
    @Test
    void roundtrip() throws Exception {
        FrameCodec c = new FrameCodec();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        Message m = new Message(MessageType.MSG, Map.of("text","hi","room","lobby"));
        c.write(out, m);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Message r = c.read(in);
        assertEquals(MessageType.MSG, r.type());
        assertEquals("hi", r.get("text"));
        assertEquals("lobby", r.get("room"));
    }

    @Test
    void nullValueTreatedAsEmpty() throws Exception {
        FrameCodec c = new FrameCodec();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        Map<String,String> f = new HashMap<>();
        f.put("msg", null);
        Message m = new Message(MessageType.NOTICE, f);
        c.write(dos, m);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Message r = c.read(in);
        assertEquals("", r.get("msg"));
    }

    @Test
    void unknownTypeRejected() throws Exception {
        FrameCodec c = new FrameCodec();
        byte[] frame = badFrame("NOT_A_TYPE", Map.of());
        assertThrows(IOException.class, () -> c.read(new DataInputStream(new ByteArrayInputStream(frame))));
    }

    @Test
    void oversizedFrameRejected() throws Exception {
        FrameCodec c = new FrameCodec();
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < FrameCodec.MAX_FRAME + 1; i++) big.append('a');
        byte[] frame = frameWithValue("MSG", "text", big.toString());
        assertThrows(IOException.class, () -> c.read(new DataInputStream(new ByteArrayInputStream(frame))));
    }

    @Test
    void trailingBytesRejected() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        byte[] type = "MSG".getBytes(StandardCharsets.UTF_8);
        dos.writeShort(type.length); dos.write(type);
        dos.writeInt(0);
        dos.writeByte(0xFF);
        byte[] payload = baos.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(out);
        o.writeInt(payload.length); o.write(payload);
        FrameCodec c = new FrameCodec();
        assertThrows(IOException.class, () -> c.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray()))));
    }

    private static byte[] badFrame(String typeName, Map<String,String> fields) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        byte[] tb = typeName.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(tb.length); dos.write(tb);
        dos.writeInt(fields.size());
        for (var e : fields.entrySet()) {
            byte[] k = e.getKey().getBytes(StandardCharsets.UTF_8);
            byte[] v = e.getValue().getBytes(StandardCharsets.UTF_8);
            dos.writeShort(k.length); dos.write(k);
            dos.writeInt(v.length); dos.write(v);
        }
        byte[] payload = baos.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(out);
        o.writeInt(payload.length); o.write(payload);
        return out.toByteArray();
    }

    private static byte[] frameWithValue(String type, String key, String value) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        byte[] tb = type.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(tb.length); dos.write(tb);
        dos.writeInt(1);
        byte[] k = key.getBytes(StandardCharsets.UTF_8);
        byte[] v = value.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(k.length); dos.write(k);
        dos.writeInt(v.length); dos.write(v);
        byte[] payload = baos.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(out);
        o.writeInt(payload.length); o.write(payload);
        return out.toByteArray();
    }
}
