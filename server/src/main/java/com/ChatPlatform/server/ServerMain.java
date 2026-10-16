package com.ChatPlatform.server;

import com.ChatPlatform.protocol.FrameCodec;
import com.ChatPlatform.protocol.Message;
import com.ChatPlatform.protocol.MessageType;
import com.ChatPlatform.server.auth.FailedLoginTracker;
import com.ChatPlatform.server.config.Config;
import com.ChatPlatform.server.rate.RateLimiterRegistry;
import com.ChatPlatform.server.store.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerMain {
    private static final Logger log = LoggerFactory.getLogger(ServerMain.class);
    private final Config config;
    private final SessionManager sm = new SessionManager();
    private final RoomManager rm = new RoomManager();
    private final Metrics metrics = new Metrics();
    private final RateLimiterRegistry rateLimiter;
    private final FailedLoginTracker failedLogin;
    private final MessageRouter router;
    private final ExecutorService writerExecutor;
    private final ScheduledExecutorService idleScheduler;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private final Map<String, Integer> perIp = new ConcurrentHashMap<>();
    private final Set<Thread> readerThreads = ConcurrentHashMap.newKeySet();

    public final UserStore userStore;
    public final HistoryStore historyStore;
    public final OfflineMessageStore offlineStore;
    public final RoomStore roomStore;

    public ServerMain(Config config) throws Exception {
        this.config = config;
        this.rateLimiter = new RateLimiterRegistry(config.rateLimitCapacity, config.rateLimitRefillMs);
        this.failedLogin = new FailedLoginTracker(config.failedLoginCap, config.failedLoginBackoffBaseMs);
        this.writerExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "db-writer"));
        this.idleScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "idle-evict"));

        StoreContext stores = buildStores(config);
        this.userStore = stores.userStore;
        this.historyStore = stores.historyStore;
        this.offlineStore = stores.offlineStore;
        this.roomStore = stores.roomStore;

        this.router = new MessageRouter(sm, rm, userStore, historyStore, offlineStore, roomStore,
            rateLimiter, failedLogin, metrics, config, writerExecutor);
        router.setClock(System::currentTimeMillis);
    }

    private ServerSocket createServerSocket() throws IOException {
        int port = config.port;
        if (!config.tlsEnabled) {
            ServerSocket ss = new ServerSocket();
            ss.bind(new InetSocketAddress(port));
            return ss;
        }
        SSLContext ctx;
        try {
            ctx = SSLContext.getInstance("TLS");
            KeyStore ks = KeyStore.getInstance("JKS");
            try (InputStream is = new FileInputStream(new File(config.tlsKeystore))) {
                ks.load(is, config.tlsKeystorePassword.toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, config.tlsKeystorePassword.toCharArray());
            ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
        } catch (Exception e) {
            throw new IOException("failed to initialize TLS context", e);
        }
        SSLServerSocketFactory ssf = ctx.getServerSocketFactory();
        ServerSocket ss = ssf.createServerSocket(port);
        if (ss instanceof SSLServerSocket ssl) {
            ssl.setNeedClientAuth(false);
            ssl.setWantClientAuth(false);
            ssl.setUseClientMode(false);
            ssl.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        }
        return ss;
    }

    private static StoreContext buildStores(Config config) throws Exception {
        switch (config.storeType) {
            case H2 -> {
                String url = "jdbc:h2:file:" + config.dbPath + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
                H2Database db = new H2Database(url);
                db.init();
                return new StoreContext(
                    new H2UserStore(db), new H2HistoryStore(db),
                    new H2OfflineMessageStore(db), new H2RoomStore(db), db);
            }
            default -> {
                return new StoreContext(
                    new InMemoryUserStore(), new InMemoryHistoryStore(),
                    new InMemoryOfflineMessageStore(), new InMemoryRoomStore(), null);
            }
        }
    }

    public void start() throws IOException {
        serverSocket = createServerSocket();
        running = true;
        log.info("Server listening on {} (tls={})", config.port, config.tlsEnabled);

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "shutdown-hook"));

        idleScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            for (Session s : sm.all()) {
                if (now - s.lastActivity > config.idleTimeoutMs && !s.isActive() && s.state.get() != SessionState.CLOSED) {
                    s.close();
                    sm.unregister(s);
                }
            }
        }, 10, 10, TimeUnit.SECONDS);

        while (running) {
            Socket sock;
            try {
                sock = serverSocket.accept();
            } catch (SocketException e) {
                break; // closed
            }
            if (!running) { try { sock.close(); } catch (IOException ignored) {} break; }
            String ip = sock.getInetAddress().getHostAddress();
            if (connectionCount.get() >= config.maxConnections) {
                log.warn("global connection cap reached, rejecting {}", ip);
                try { sock.close(); } catch (IOException ignored) {}
                continue;
            }
            perIp.merge(ip, 1, Integer::sum);
            if (perIp.get(ip) > config.maxConnectionsPerIP) {
                log.warn("per-ip cap reached, rejecting {}", ip);
                perIp.merge(ip, -1, Integer::sum);
                try { sock.close(); } catch (IOException ignored) {}
                continue;
            }
            connectionCount.incrementAndGet();
            try {
                sock.setSoTimeout(config.idleTimeoutMs);
            } catch (IOException ignored) {}
            Session s = new Session(sock);
            sm.register(s);
            s.startWriter();
            Thread reader = new Thread(() -> handle(s, ip), "reader-" + s.id);
            readerThreads.add(reader);
            reader.start();
        }
    }

    public int localPort() {
        return serverSocket == null ? -1 : serverSocket.getLocalPort();
    }

    private void handle(Session s, String ip) {
        try {
            while (running && s.state.get() != SessionState.CLOSED && !s.isClosed()) {
                Message m;
                try {
                    m = s.codec.read(s.in);
                } catch (SocketException e) {
                    break; // disconnected
                } catch (java.net.SocketTimeoutException e) {
                    if (System.currentTimeMillis() - s.lastActivity > config.idleTimeoutMs) break;
                    continue;
                }
                s.touch();
                router.dispatch(s, m);
            }
        } catch (Exception e) {
            log.debug("session {} reader error: {}", s.id, e.toString());
        } finally {
            leaveRooms(s);
            sm.unregister(s);
            s.close();
            connectionCount.decrementAndGet();
            perIp.merge(ip, -1, Integer::sum);
            metrics.setSessions(sm.count());
            readerThreads.remove(Thread.currentThread());
        }
    }

    private void leaveRooms(Session s) {
        for (String r : new java.util.ArrayList<>(s.rooms)) {
            Room room = rm.get(r);
            if (room != null) {
                room.removeMember(s.username);
                roomStore.removeMember(r, s.username);
                if (s.username != null) room.broadcast(sm, new Message(MessageType.NOTICE, Map.of("msg", s.username + " left " + r)), s);
                rm.removeIfEmpty(r);
            }
        }
        s.rooms.clear();
    }

    public void shutdown() {
        if (!running) return;
        running = false;
        log.info("Shutting down server...");
        try { serverSocket.close(); } catch (IOException ignored) {}
        for (Session s : sm.all()) {
            s.enqueue(new Message(MessageType.NOTICE, Map.of("msg", "server shutting down")));
            s.close();
        }
        sm.all().clear();
        idleScheduler.shutdown();
        try { idleScheduler.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        writerExecutor.shutdown();
        try { writerExecutor.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        for (Thread t : readerThreads) t.interrupt();
        metrics.setSessions(0);
        log.info("Server shut down");
    }

    public static void main(String[] args) throws Exception {
        String configPath = null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i])) configPath = args[i + 1];
        }
        int port = args.length > 0 && !"--config".equals(args[0]) ? Integer.parseInt(args[0]) : 5000;
        Config config = Config.load(configPath);
        config.port = port;
        ServerMain server = new ServerMain(config);
        server.start();
    }

    private static final class StoreContext {
        final UserStore userStore;
        final HistoryStore historyStore;
        final OfflineMessageStore offlineStore;
        final RoomStore roomStore;
        final H2Database db;
        StoreContext(UserStore us, HistoryStore hs, OfflineMessageStore os, RoomStore rs, H2Database db){
            this.userStore = us; this.historyStore = hs; this.offlineStore = os; this.roomStore = rs; this.db = db;
        }
    }
}
