package com.ChatPlatform.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class FrameCodec {
    public static final int MAX_FRAME = 1 << 20;
    public static final int MAX_FIELD = 1 << 16;
    public static final int MAX_FIELD_COUNT = 64;
    public static final int MAX_TYPE_LEN = 64;

    public void write(DataOutputStream out, Message m) throws IOException {
        if (m == null || m.type() == null) throw new IOException("null message");
        byte[] typeBytes = m.type().name().getBytes(StandardCharsets.UTF_8);
        if (typeBytes.length == 0 || typeBytes.length > MAX_TYPE_LEN) {
            throw new IOException("bad type length: " + typeBytes.length);
        }
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
        dos.writeShort(typeBytes.length);
        dos.write(typeBytes);
        Map<String,String> f = m.fields() == null ? Map.of() : m.fields();
        if (f.size() > MAX_FIELD_COUNT) throw new IOException("too many fields");
        dos.writeInt(f.size());
        for (Map.Entry<String,String> e : f.entrySet()) {
            if (e.getKey() == null) throw new IOException("null field key");
            byte[] k = e.getKey().getBytes(StandardCharsets.UTF_8);
            String v = e.getValue() == null ? "" : e.getValue();
            byte[] val = v.getBytes(StandardCharsets.UTF_8);
            if (k.length > MAX_FIELD || val.length > MAX_FRAME) throw new IOException("field too large");
            dos.writeShort(k.length);
            dos.write(k);
            dos.writeInt(val.length);
            dos.write(val);
        }
        dos.flush();
        byte[] payload = baos.toByteArray();
        if (payload.length > MAX_FRAME) throw new IOException("frame too large");
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    public Message read(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len <= 0 || len > MAX_FRAME) throw new IOException("bad frame length: " + len);
        byte[] buf = new byte[len];
        in.readFully(buf);
        java.io.DataInputStream dis = new java.io.DataInputStream(new java.io.ByteArrayInputStream(buf));
        int tl = dis.readUnsignedShort();
        if (tl == 0 || tl > MAX_TYPE_LEN) throw new IOException("bad type length");
        byte[] tb = new byte[tl];
        dis.readFully(tb);
        String typeName = new String(tb, StandardCharsets.UTF_8);
        MessageType type;
        try {
            type = MessageType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            throw new IOException("unknown message type: " + typeName);
        }
        int n = dis.readInt();
        if (n < 0 || n > MAX_FIELD_COUNT) throw new IOException("bad field count");
        Map<String,String> fields = new HashMap<>(n);
        for (int i = 0; i < n; i++) {
            int kl = dis.readUnsignedShort();
            byte[] kb = new byte[kl];
            dis.readFully(kb);
            int vl = dis.readInt();
            if (vl < 0 || vl > MAX_FRAME) throw new IOException("bad value length");
            byte[] vb = new byte[vl];
            dis.readFully(vb);
            fields.put(new String(kb, StandardCharsets.UTF_8), new String(vb, StandardCharsets.UTF_8));
        }
        if (dis.available() > 0) throw new IOException("trailing bytes in frame");
        return new Message(type, fields);
    }
}
