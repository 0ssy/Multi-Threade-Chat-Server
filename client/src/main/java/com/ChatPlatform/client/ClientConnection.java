package com.ChatPlatform.client;

import com.ChatPlatform.protocol.FrameCodec;
import com.ChatPlatform.protocol.Message;
import com.ChatPlatform.protocol.MessageType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientConnection {
    public interface Listener {
        void onMessage(Message m);
        void onDisconnected();
        void onConnectFailed(Throwable t);
    }

    private final String host;
    private final int port;
    private final Listener listener;
    private final FrameCodec codec = new FrameCodec();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Set<String> rooms = new CopyOnWriteArraySet<>();
    private String username;
    private String password;
    private volatile Socket socket;
    private volatile DataOutputStream out;
    private volatile DataInputStream in;
    private Thread readThread;
    private int reconnectAttempts = 0;
    private final Random random = new Random();

    public ClientConnection(String host, int port, String username, String password, Listener listener) {
        this.host = host; this.port = port; this.username = username; this.password = password; this.listener = listener;
    }

    public synchronized void connect() throws IOException {
        doConnect();
    }

    private void doConnect() throws IOException {
        Socket s = new Socket(host, port);
        s.setSoTimeout(10000);
        s.setTcpNoDelay(true);
        socket = s;
        out = new DataOutputStream(s.getOutputStream());
        in = new DataInputStream(s.getInputStream());
        connected.set(true);
        reconnectAttempts = 0;
        codec.write(out, new Message(MessageType.HELLO, Map.of("version","1")));
        Message reply = codec.read(in);
        if (reply.type() == MessageType.ERROR && "6".equals(reply.get("code"))) {
            throw new IOException("version mismatch");
        }
        if (!authenticate()) throw new IOException("auth failed");
        running.set(true);
        readThread = new Thread(this::readLoop, "client-reader");
        readThread.setDaemon(true);
        readThread.start();
    }

    private boolean authenticate() throws IOException {
        codec.write(out, new Message(MessageType.AUTH, Map.of("username", username, "password", password, "mode", "login")));
        Message reply = codec.read(in);
        if (reply.type() == MessageType.AUTH_OK) return true;
        // try register if login failed (user may not exist yet)
        codec.write(out, new Message(MessageType.AUTH, Map.of("username", username, "password", password, "mode", "register")));
        reply = codec.read(in);
        return reply.type() == MessageType.AUTH_OK;
    }

    public void send(Message m) {
        DataOutputStream o = out;
        if (o == null) return;
        try { codec.write(o, m); } catch (IOException ignored) { scheduleReconnect(); }
    }

    public void join(String room) {
        rooms.add(room);
        send(new Message(MessageType.JOIN, Map.of("room", room)));
    }

    public void leave(String room) {
        rooms.remove(room);
        send(new Message(MessageType.LEAVE, Map.of("room", room)));
    }

    public void presence(String status) {
        send(new Message(MessageType.PRESENCE, Map.of("status", status)));
    }

    public void typing(String room) {
        send(new Message(MessageType.TYPING, Map.of("room", room)));
    }

    public void history(String room, String before, int limit) {
        send(new Message(MessageType.HISTORY, Map.of("room", room, "before", before == null ? "" : before, "limit", String.valueOf(limit))));
    }

    private void readLoop() {
        try {
            while (running.get() && connected.get()) {
                Message m = codec.read(in);
                listener.onMessage(m);
            }
        } catch (IOException e) {
            if (running.get()) listener.onDisconnected();
        }
    }

    private synchronized void scheduleReconnect() {
        if (!running.get()) return;
        connected.set(false);
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        listener.onDisconnected();
        new Thread(() -> {
            int base = 1000;
            while (running.get()) {
                int attempt = ++reconnectAttempts;
                long backoff = (long) (base * Math.pow(2, Math.min(attempt - 1, 6))) + random.nextInt(1000);
                try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                if (!running.get()) return;
                try {
                    doConnect();
                    // reauth & rejoin
                    for (String r : new CopyOnWriteArraySet<>(rooms)) join(r);
                    listener.onMessage(new Message(MessageType.NOTICE, Map.of("msg","reconnected")));
                    return;
                } catch (IOException e) {
                    // retry
                }
            }
        }, "client-reconnect").start();
    }

    public Set<String> rooms() { return rooms; }
    public String username() { return username; }
    public boolean isConnected() { return connected.get(); }

    public void close() {
        running.set(false);
        connected.set(false);
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        if (readThread != null) readThread.interrupt();
    }
}
