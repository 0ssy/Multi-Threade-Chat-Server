package com.ChatPlatform.server;

import com.ChatPlatform.protocol.FrameCodec;
import com.ChatPlatform.protocol.Message;
import com.ChatPlatform.protocol.MessageType;
import com.ChatPlatform.server.config.Config;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(60)
class ChatIntegrationTest {
    private ServerMain server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        Config cfg = new Config();
        cfg.port = 0;
        server = new ServerMain(cfg);
        Thread t = new Thread(() -> {
            try { server.start(); } catch (IOException e) { throw new RuntimeException(e); }
        });
        t.start();
        long deadline = System.currentTimeMillis() + 10000;
        while (server.localPort() <= 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        port = server.localPort();
        assertTrue(port > 0, "server did not bind");
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.shutdown();
    }

    static class ClientHandle implements AutoCloseable {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        final FrameCodec codec = new FrameCodec();
        final BlockingQueue<Message> received = new LinkedBlockingQueue<>(256);
        volatile Thread reader;
        volatile boolean running = true;

        ClientHandle(int port) throws IOException {
            socket = new Socket("localhost", port);
            socket.setSoTimeout(0);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        }

        void startReader() {
            reader = new Thread(() -> {
                while (running) {
                    try {
                        Message m = codec.read(in);
                        received.offer(m);
                    } catch (Exception e) { running = false; return; }
                }
            }, "client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        void send(Message m) throws IOException { codec.write(out, m); }
        void join(String room) throws IOException { send(new Message(MessageType.JOIN, Map.of("room", room))); }

        Message poll(long ms) throws InterruptedException { return received.poll(ms, TimeUnit.MILLISECONDS); }
        Message waitFor(long ms) throws InterruptedException {
            long deadline = System.currentTimeMillis() + ms;
            while (System.currentTimeMillis() < deadline) {
                Message m = received.poll(100, TimeUnit.MILLISECONDS);
                if (m != null) return m;
            }
            return null;
        }
        void drain() throws InterruptedException {
            while (received.poll(100, TimeUnit.MILLISECONDS) != null) {}
        }

        void hello() throws IOException { send(new Message(MessageType.HELLO, Map.of("version", "1"))); }
        Message expectHello() throws InterruptedException {
            Message m = poll(3000);
            assertNotNull(m);
            assertEquals(MessageType.HELLO, m.type());
            return m;
        }

        void auth(String user, String pass, String mode) throws IOException {
            send(new Message(MessageType.AUTH, Map.of("username", user, "password", pass, "mode", mode)));
        }
        Message expectAuthOk() throws InterruptedException {
            Message m = poll(3000);
            assertNotNull(m);
            assertEquals(MessageType.AUTH_OK, m.type());
            return m;
        }
        void expectJoined() throws InterruptedException {
            Message m = poll(3000);
            assertNotNull(m);
            assertEquals(MessageType.JOINED, m.type());
        }

        @Override
        public void close() throws IOException {
            running = false;
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private ClientHandle connectAndAuth(String user, String pass) throws Exception {
        ClientHandle c = new ClientHandle(port);
        c.startReader();
        c.hello();
        c.expectHello();
        c.auth(user, pass, "login");
        Message r = c.poll(3000);
        if (r == null || r.type() != MessageType.AUTH_OK) {
            c.auth(user, pass, "register");
            r = c.poll(3000);
        }
        assertNotNull(r);
        assertEquals(MessageType.AUTH_OK, r.type());
        return c;
    }

    @Test
    void fullFlowAuthJoinMsgDmHistory() throws Exception {
        port = server.localPort();
        assertTrue(port > 0);
        ClientHandle a = connectAndAuth("alice", "pass");
        ClientHandle b = connectAndAuth("bob", "pass");
        a.join("lobby");
        a.expectJoined();
        b.join("lobby");
        b.expectJoined();
        a.drain(); b.drain();
        a.send(new Message(MessageType.MSG, Map.of("room", "lobby", "text", "hi bob")));
        Message mb = b.poll(3000);
        assertNotNull(mb);
        assertEquals(MessageType.MSG, mb.type());
        assertEquals("alice", mb.get("from"));
        assertEquals("hi bob", mb.get("text"));
        assertNotNull(mb.get("id"));
        assertNotNull(mb.get("ts"));
        // DM alice->bob
        a.send(new Message(MessageType.DM, Map.of("to", "bob", "text", "secret")));
        Message dm = b.poll(3000);
        assertNotNull(dm); assertEquals(MessageType.DM, dm.type());
        assertEquals("alice", dm.get("from"));
        // history (allow async persistence to drain)
        Thread.sleep(300);
        a.drain();
        a.send(new Message(MessageType.HISTORY, Map.of("room", "lobby", "limit", "5")));
        Message h = a.poll(3000);
        assertNotNull(h); assertEquals(MessageType.HISTORY, h.type());
        a.close(); b.close();
    }

    @Test
    void duplicateLoginKicksOld() throws Exception {
        port = server.localPort();
        ClientHandle a = connectAndAuth("carol", "pass");
        ClientHandle a2 = connectAndAuth("carol", "pass");
        Message kicked = a.poll(3000);
        assertNotNull(kicked);
        assertTrue(kicked.type() == MessageType.ERROR || kicked.type() == MessageType.NOTICE);
        a.close(); a2.close();
    }

    @Test
    void offlineQueueFlushedAtLogin() throws Exception {
        port = server.localPort();
        ClientHandle a = connectAndAuth("dave", "pass");
        a.close(); // dave goes offline
        ClientHandle b = connectAndAuth("eve", "pass");
        b.send(new Message(MessageType.DM, Map.of("to", "dave", "text", "offline msg")));
        Thread.sleep(200);
        b.close();
        ClientHandle a2 = connectAndAuth("dave", "pass");
        Message flushed = a2.poll(3000);
        assertNotNull(flushed);
        assertEquals(MessageType.DM, flushed.type());
        assertEquals("offline msg", flushed.get("text"));
        a2.close();
    }

    @Test
    void moderationPermissions() throws Exception {
        port = server.localPort();
        ClientHandle owner = connectAndAuth("own", "pass");
        ClientHandle mod = connectAndAuth("mord", "pass");
        ClientHandle mem = connectAndAuth("mem", "pass");
        owner.join("modroom");
        owner.expectJoined();
        mod.join("modroom");
        mod.expectJoined();
        mem.join("modroom");
        mem.expectJoined();
        Thread.sleep(300);
        mem.drain();
        // member tries kick -> permission denied
        mem.send(new Message(MessageType.KICK, Map.of("room", "modroom", "target", "mord")));
        Message err = mem.waitFor(3000);
        assertNotNull(err); assertEquals(MessageType.ERROR, err.type());
        assertEquals("8", err.get("code"));
        // owner kicks mod
        mod.drain();
        owner.send(new Message(MessageType.KICK, Map.of("room", "modroom", "target", "mord")));
        Message kicked = mod.waitFor(3000);
        assertNotNull(kicked); assertEquals(MessageType.KICK, kicked.type());
        owner.close(); mod.close(); mem.close();
    }

    @Test
    void topicAndLock() throws Exception {
        port = server.localPort();
        ClientHandle o = connectAndAuth("towner", "pass");
        ClientHandle m2 = connectAndAuth("tmem", "pass");
        o.join("troom");
        o.expectJoined();
        m2.join("troom");
        m2.expectJoined();
        Thread.sleep(300);
        o.drain(); m2.drain();
        o.send(new Message(MessageType.LOCK, Map.of("room", "troom")));
        Message lk = m2.waitFor(3000);
        assertNotNull(lk); assertEquals(MessageType.LOCK, lk.type());
        m2.drain();
        o.send(new Message(MessageType.TOPIC, Map.of("room", "troom", "topic", "new topic")));
        Message tp = m2.waitFor(3000);
        assertNotNull(tp); assertEquals(MessageType.TOPIC, tp.type());
        o.close(); m2.close();
    }

    @Test
    void adminStats() throws Exception {
        port = server.localPort();
        ClientHandle a = connectAndAuth("admin", "pass");
        a.send(new Message(MessageType.ADMIN, Map.of("cmd", "stats")));
        Message st = a.poll(3000);
        assertNotNull(st); assertEquals(MessageType.NOTICE, st.type());
        assertTrue(st.get("msg").contains("messages="));
        a.close();
    }
}
