package com.ChatPlatform.tools;

import com.ChatPlatform.protocol.FrameCodec;
import com.ChatPlatform.protocol.Message;
import com.ChatPlatform.protocol.MessageType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class Fuzzer {
    static final AtomicLong CONNECTIONS = new AtomicLong();
    static final AtomicLong FRAMES = new AtomicLong();
    static final AtomicLong ERRORS = new AtomicLong();

    public static void main(String[] args) throws Exception {
        int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 2000;
        String host = args.length > 1 ? args[1] : "localhost";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 5000;
        int seed = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        Random rnd = new Random(seed);

        for (int i = 0; i < iterations; i++) {
            try (Socket s = new Socket(host, port)) {
                s.setSoTimeout(3000);
                s.setTcpNoDelay(true);
                CONNECTIONS.incrementAndGet();
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                FrameCodec codec = new FrameCodec();
                int mode = rnd.nextInt(8);
                switch (mode) {
                    case 0 -> fuzzRandomBytes(out);
                    case 1 -> fuzzTruncatedFrame(out);
                    case 2 -> fuzzMalformedType(codec, out);
                    case 3 -> fuzzSemanticallyInvalid(codec, out, rnd);
                    case 4 -> fuzzOversize(codec, out);
                    case 5 -> fuzzValidButWeird(codec, out, rnd);
                    case 6 -> fuzzHalfFrame(out, rnd);
                    case 7 -> fuzzValidHelloAuth(codec, out);
                }
                FRAMES.incrementAndGet();
            } catch (IOException e) {
                ERRORS.incrementAndGet();
            }
        }
        System.out.printf("Fuzz done: iterations=%d connections=%d frames=%d errors=%d%n",
            iterations, CONNECTIONS.get(), FRAMES.get(), ERRORS.get());
    }

    static void fuzzRandomBytes(java.io.OutputStream out) throws IOException {
        Random r = new Random();
        byte[] b = new byte[r.nextInt(4096)];
        r.nextBytes(b);
        out.write(b);
        out.flush();
    }

    static void fuzzTruncatedFrame(DataOutputStream out) throws IOException {
        // write a length that claims more bytes than follow
        out.writeInt(100000);
        out.write(new byte[10]);
        out.flush();
    }

    static void fuzzMalformedType(FrameCodec codec, DataOutputStream out) throws IOException {
        Random r = new Random();
        byte[] payload = buildPayload("!!!NOT_A_TYPE!!!", Map.of("x", "y"));
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    static void fuzzSemanticallyInvalid(FrameCodec codec, DataOutputStream out, Random rnd) throws IOException {
        MessageType[] types = MessageType.values();
        MessageType t = types[rnd.nextInt(types.length)];
        Map<String,String> f = Map.of("username", "", "room", "", "text", "");
        codec.write(out, new Message(t, f));
    }

    static void fuzzOversize(FrameCodec codec, DataOutputStream out) throws IOException {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < FrameCodec.MAX_FRAME + 100; i++) big.append('x');
        codec.write(out, new Message(MessageType.MSG, Map.of("room", "r", "text", big.toString())));
    }

    static void fuzzValidButWeird(FrameCodec codec, DataOutputStream out, Random rnd) throws IOException {
        codec.write(out, new Message(MessageType.MSG, Map.of("room", "a".repeat(100), "text", "z".repeat(10000))));
    }

    static void fuzzHalfFrame(DataOutputStream out, Random rnd) throws IOException {
        byte[] b = new byte[rnd.nextInt(20)];
        rnd.nextBytes(b);
        out.write(b, 0, b.length / 2);
        out.flush();
    }

    static void fuzzValidHelloAuth(FrameCodec codec, DataOutputStream out) throws IOException {
        codec.write(out, new Message(MessageType.HELLO, Map.of("version", "1")));
        codec.write(out, new Message(MessageType.AUTH, Map.of("username", "fuzz", "password", "pass", "mode", "register")));
    }

    static byte[] buildPayload(String typeName, Map<String,String> fields) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
        byte[] tb = typeName.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(tb.length);
        dos.write(tb);
        dos.writeInt(fields.size());
        for (Map.Entry<String,String> e : fields.entrySet()) {
            byte[] k = e.getKey().getBytes(StandardCharsets.UTF_8);
            byte[] v = e.getValue().getBytes(StandardCharsets.UTF_8);
            dos.writeShort(k.length);
            dos.write(k);
            dos.writeInt(v.length);
            dos.write(v);
        }
        dos.flush();
        return baos.toByteArray();
    }
}
