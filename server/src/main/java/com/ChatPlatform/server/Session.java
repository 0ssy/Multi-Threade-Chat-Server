package com.ChatPlatform.server;

import com.ChatPlatform.protocol.FrameCodec;
import com.ChatPlatform.protocol.Message;
import com.ChatPlatform.protocol.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class Session {
    private static final Logger log = LoggerFactory.getLogger(Session.class);
    private static final int QCAP = 256;
    private static final AtomicLong SEQ = new AtomicLong(0);
    public final long id;
    public final Socket socket;
    public final DataInputStream in;
    public final DataOutputStream out;
    public final FrameCodec codec = new FrameCodec();
    public final LinkedBlockingQueue<Message> outQueue = new LinkedBlockingQueue<>(QCAP);
    public final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.CONNECTED);
    public String username;
    public final Set<String> rooms = ConcurrentHashMap.newKeySet();
    public volatile long lastActivity = System.currentTimeMillis();
    private Thread writerThread;
    private volatile boolean closed = false;

    public Session(Socket socket) throws IOException {
        this.id = SEQ.incrementAndGet();
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    public boolean transition(SessionState from, SessionState to) {
        return state.compareAndSet(from, to);
    }

    public void startWriter() {
        writerThread = new Thread(() -> {
            while (true) {
                SessionState st = state.get();
                if (st == SessionState.CLOSED) break;
                try {
                    Message m = outQueue.take();
                    if (st == SessionState.CLOSING) continue;
                    codec.write(out, m);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    log.debug("write failed session {}", id);
                    break;
                }
            }
        }, "writer-" + id);
        writerThread.start();
    }

    public boolean enqueue(Message m) {
        if (closed) return false;
        if (state.get() == SessionState.CLOSED) return false;
        if (!outQueue.offer(m)) {
            close();
            return false;
        }
        return true;
    }

    public void close() {
        if (closed) return;
        closed = true;
        state.set(SessionState.CLOSED);
        drainQueue();
        try { socket.close(); } catch (IOException ignored) {}
        if (writerThread != null) writerThread.interrupt();
    }

    private void drainQueue() {
        Message m;
        while ((m = outQueue.poll()) != null) {
            try { codec.write(out, m); } catch (IOException ignored) { break; }
        }
    }

    public boolean isActive() { return state.get() == SessionState.ACTIVE; }
    public boolean isClosed(){ return closed; }
    public void touch() { lastActivity = System.currentTimeMillis(); }
    public int queueDepth(){ return outQueue.size(); }
}
