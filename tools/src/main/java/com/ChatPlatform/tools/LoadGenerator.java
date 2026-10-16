package com.ChatPlatform.tools;

import com.ChatPlatform.protocol.FrameCodec;
import com.ChatPlatform.protocol.Message;
import com.ChatPlatform.protocol.MessageType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class LoadGenerator {
    static final AtomicLong SENT = new AtomicLong();
    static final AtomicLong OK = new AtomicLong();
    static final AtomicLong ERR = new AtomicLong();

    public static void main(String[] args) throws Exception {
        int clients = parse(args, 0, 50);
        int rooms = parse(args, 1, 5);
        int rateMs = parse(args, 2, 200);
        int durationSec = parse(args, 3, 10);
        String host = args.length > 4 ? args[4] : "localhost";
        int port = args.length > 5 ? Integer.parseInt(args[5]) : 5000;

        Random rnd = new Random();
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        long end = System.currentTimeMillis() + durationSec * 1000L;
        for (int i = 0; i < clients; i++) {
            final int id = i;
            Thread t = new Thread(() -> {
                try {
                    runClient(host, port, rnd, rooms, rateMs, end, id);
                } catch (Exception ignored) {}
            }, "load-" + id);
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) t.join();
        System.out.printf("Load done: sent=%d ok=%d err=%d clients=%d rooms=%d rateMs=%d dur=%ds%n",
            SENT.get(), OK.get(), ERR.get(), clients, rooms, rateMs, durationSec);
    }

    static void runClient(String host, int port, Random rnd, int rooms, int rateMs, long end, int id) throws Exception {
        Socket s = new Socket(host, port);
        s.setSoTimeout(5000);
        DataOutputStream out = new DataOutputStream(s.getOutputStream());
        DataInputStream in = new DataInputStream(s.getInputStream());
        FrameCodec codec = new FrameCodec();
        String user = "loaduser" + id;
        codec.write(out, new Message(MessageType.HELLO, Map.of("version", "1")));
        codec.read(in);
        codec.write(out, new Message(MessageType.AUTH, Map.of("username", user, "password", "pass", "mode", "register")));
        Message r = codec.read(in);
        boolean authed = r.type() == MessageType.AUTH_OK;
        if (!authed) {
            codec.write(out, new Message(MessageType.AUTH, Map.of("username", user, "password", "pass", "mode", "register")));
            r = codec.read(in);
            authed = r.type() == MessageType.AUTH_OK;
        }
        String room = "room" + (id % rooms);
        if (authed) {
            codec.write(out, new Message(MessageType.JOIN, Map.of("room", room)));
            codec.read(in);
        }
        long next = System.currentTimeMillis();
        while (System.currentTimeMillis() < end && !s.isClosed()) {
            if (!authed) break;
            String text = "load-msg-" + id + "-" + System.currentTimeMillis();
            try {
                codec.write(out, new Message(MessageType.MSG, Map.of("room", room, "text", text)));
                SENT.incrementAndGet();
                OK.incrementAndGet();
            } catch (Exception e) {
                ERR.incrementAndGet();
                break;
            }
            next += rateMs;
            long delay = next - System.currentTimeMillis();
            if (delay > 0) Thread.sleep(delay);
        }
        try { s.close(); } catch (Exception ignored) {}
    }

    static int parse(String[] args, int idx, int def) {
        if (args.length <= idx) return def;
        try { return Integer.parseInt(args[idx]); } catch (NumberFormatException e) { return def; }
    }
}
