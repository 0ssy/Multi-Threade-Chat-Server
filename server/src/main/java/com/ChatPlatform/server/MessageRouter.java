package com.ChatPlatform.server;

import com.ChatPlatform.protocol.Message;
import com.ChatPlatform.protocol.MessageType;
import com.ChatPlatform.protocol.ProtocolValidator;
import com.ChatPlatform.server.auth.FailedLoginTracker;
import com.ChatPlatform.server.config.Config;
import com.ChatPlatform.server.rate.RateLimiterRegistry;
import com.ChatPlatform.server.store.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class MessageRouter {
    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);
    private final SessionManager sm;
    private final RoomManager rm;
    private final UserStore userStore;
    private final HistoryStore historyStore;
    private final OfflineMessageStore offlineStore;
    private final RoomStore roomStore;
    private final RateLimiterRegistry rateLimiter;
    private final FailedLoginTracker failedLogin;
    private final Metrics metrics;
    private final Config config;
    private final Executor writerExecutor;
    private final AtomicLong msgId = new AtomicLong(1);
    private LongSupplier clock = System::currentTimeMillis;

    public MessageRouter(SessionManager sm, RoomManager rm, UserStore userStore,
                         HistoryStore historyStore, OfflineMessageStore offlineStore,
                         RoomStore roomStore, RateLimiterRegistry rateLimiter,
                         FailedLoginTracker failedLogin, Metrics metrics, Config config,
                         Executor writerExecutor) {
        this.sm = sm; this.rm = rm; this.userStore = userStore; this.historyStore = historyStore;
        this.offlineStore = offlineStore; this.roomStore = roomStore; this.rateLimiter = rateLimiter;
        this.failedLogin = failedLogin; this.metrics = metrics; this.config = config;
        this.writerExecutor = writerExecutor;
    }

    public void setClock(LongSupplier clock){ this.clock = clock; }
    public long now(){ return clock.getAsLong(); }

    public void dispatch(Session s, Message m) {
        MessageType t = m.type();
        switch (t) {
            case HELLO -> handleHello(s, m);
            case AUTH -> handleAuth(s, m);
            case JOIN -> handleJoin(s, m);
            case LEAVE -> handleLeave(s, m);
            case MSG -> handleMsg(s, m);
            case DM -> handleDm(s, m);
            case LIST_ROOMS -> handleListRooms(s, m);
            case LIST_USERS -> handleListUsers(s, m);
            case PING -> s.enqueue(new Message(MessageType.PONG, Map.of()));
            case HISTORY -> handleHistory(s, m);
            case PRESENCE -> handlePresence(s, m);
            case TYPING -> handleTyping(s, m);
            case KICK, BAN, MUTE, TOPIC, LOCK -> handleModeration(s, m);
            case ADMIN -> handleAdmin(s, m);
            default -> error(s, "10", "unknown message type");
        }
    }

    private void handleHello(Session s, Message m) {
        if (s.state.get() != SessionState.CONNECTED) { error(s, "10", "unexpected"); return; }
        try {
            ProtocolValidator.version(m.fields());
            s.transition(SessionState.CONNECTED, SessionState.AUTHENTICATING);
            s.enqueue(new Message(MessageType.HELLO, Map.of("version","1")));
        } catch (ProtocolValidator.ProtocolException e) {
            error(s, "6", "version mismatch");
        }
    }

    private void handleAuth(Session s, Message m) {
        if (s.state.get() != SessionState.AUTHENTICATING) { error(s, "2", "not authenticating"); return; }
        String mode = m.get("mode");
        try {
            String u = ProtocolValidator.username(m.fields());
            String p = ProtocolValidator.password(m.fields());
            String key = "auth:" + u;
            if (failedLogin.isBlocked(key)) { error(s, "5", "auth temporarily blocked"); return; }
            if (!rateLimiter.tryAcquire("auth:" + u)) { error(s, "4", "rate limited"); return; }

            if ("register".equals(mode)) {
                if (userStore.exists(u)) { error(s, "7", "user exists"); return; }
                String hash = org.mindrot.jbcrypt.BCrypt.hashpw(p, org.mindrot.jbcrypt.BCrypt.gensalt());
                String user = u;
                writerExecutor.execute(() -> userStore.register(user, hash));
                sm.bindUser(s, user);
                s.transition(SessionState.AUTHENTICATING, SessionState.ACTIVE);
                failedLogin.reset(user);
                s.enqueue(new Message(MessageType.AUTH_OK, Map.of("msg","welcome " + user)));
                flushOffline(s);
            } else {
                String hash = userStore.getHash(u);
                boolean ok = hash != null && org.mindrot.jbcrypt.BCrypt.checkpw(p, hash);
                if (!ok) {
                    failedLogin.recordFailure(u);
                    metrics.incAuthFailures();
                    error(s, "5", "auth failed");
                    return;
                }
                Session old = sm.bindUser(s, u);
                if (old != null) old.enqueue(new Message(MessageType.NOTICE, Map.of("msg","you were logged in elsewhere")));
                s.transition(SessionState.AUTHENTICATING, SessionState.ACTIVE);
                failedLogin.reset(u);
                s.enqueue(new Message(MessageType.AUTH_OK, Map.of("msg","logged in")));
                flushOffline(s);
            }
        } catch (ProtocolValidator.ProtocolException e) {
            error(s, "5", "invalid credentials");
        }
    }

    private void flushOffline(Session s) {
        List<String> q = offlineStore.takeOffline(s.username);
        for (String entry : q) {
            String[] parts = entry.split(":", 4);
            if (parts.length < 4) continue;
            s.enqueue(new Message(MessageType.DM, Map.of("from", parts[1], "text", parts[2], "id", parts[0], "ts", parts[3])));
        }
    }

    private void handleJoin(Session s, Message m) {
        if (!s.isActive()) { error(s, "2", "not authenticated"); return; }
        try {
            String room = ProtocolValidator.room(m.fields());
            if (!rateLimiter.tryAcquire("join:" + s.username)) { error(s, "4", "rate limited"); return; }
            Room r = rm.getOrCreate(room, s.username);
            Room.Role role = r.isMember(s.username) ? r.getRole(s.username) : Room.Role.MEMBER;
            if (!r.isMember(s.username) && r.memberCount() >= r.memberLimit) { error(s, "8", "room full"); return; }
            if (r.banned.contains(s.username)) { error(s, "8", "banned from room"); return; }
            if (!r.isMember(s.username)) r.addMember(s.username, role);
            s.rooms.add(room);
            String user = s.username;
            writerExecutor.execute(() -> roomStore.setMemberRole(room, user, role.name()));
            metrics.incJoins();
            s.enqueue(new Message(MessageType.JOINED, Map.of("room", room, "role", role.name())));
            r.broadcast(sm, new Message(MessageType.NOTICE, Map.of("msg", s.username + " joined " + room)), s);
            replayHistory(s, room, config.historyLimit);
        } catch (ProtocolValidator.ProtocolException e) {
            error(s, "9", "invalid room");
        }
    }

    private void replayHistory(Session s, String room, int n) {
        List<String> hist = historyStore.lastN(room, n);
        for (String entry : hist) {
            String[] parts = entry.split(":", 4);
            if (parts.length < 4) continue;
            s.enqueue(new Message(MessageType.HISTORY, Map.of("room", room, "id", parts[0], "from", parts[1], "text", parts[2], "ts", parts[3])));
        }
    }

    private void handleLeave(Session s, Message m) {
        if (!s.isActive()) return;
        try {
            String room = ProtocolValidator.room(m.fields());
            Room r = rm.get(room);
            if (r != null && r.removeMember(s.username)) {
                s.rooms.remove(room);
                String user = s.username;
                writerExecutor.execute(() -> roomStore.removeMember(room, user));
                metrics.incLeaves();
                r.broadcast(sm, new Message(MessageType.NOTICE, Map.of("msg", s.username + " left " + room)), s);
                rm.removeIfEmpty(room);
            }
        } catch (ProtocolValidator.ProtocolException ignored) {}
    }

    private void handleMsg(Session s, Message m) {
        if (!s.isActive()) return;
        try {
            String room = ProtocolValidator.room(m.fields());
            String text = ProtocolValidator.text(m.fields());
            if (!rateLimiter.tryAcquire("msg:" + s.username)) { error(s, "4", "rate limited"); return; }
            Room r = rm.get(room);
            if (r == null || !r.isMember(s.username)) { error(s, "3", "no such room"); return; }
            if (r.locked && !r.isModerator(s.username)) { error(s, "8", "room locked"); return; }
            if (r.muted.contains(s.username)) { error(s, "8", "muted"); return; }
            long id = msgId.getAndIncrement();
            long ts = now();
            String from = s.username;
            Message out = new Message(MessageType.MSG, Map.of("room", room, "from", from, "text", text, "id", String.valueOf(id), "ts", String.valueOf(ts)));
            writerExecutor.execute(() -> historyStore.save(room, from, text, id, ts));
            r.broadcast(sm, out, s);
            s.enqueue(out);
            metrics.incMessages();
        } catch (ProtocolValidator.ProtocolException e) {
            error(s, "9", "invalid message");
        }
    }

    private void handleDm(Session s, Message m) {
        if (!s.isActive()) return;
        try {
            String to = m.get("to");
            String text = ProtocolValidator.text(m.fields());
            if (to == null || to.isEmpty()) { error(s, "9", "invalid dm"); return; }
            if (!rateLimiter.tryAcquire("dm:" + s.username)) { error(s, "4", "rate limited"); return; }
            long id = msgId.getAndIncrement();
            long ts = now();
            String from = s.username;
            Message out = new Message(MessageType.DM, Map.of("from", from, "to", to, "text", text, "id", String.valueOf(id), "ts", String.valueOf(ts)));
            Session target = sm.byUsername(to);
            if (target != null && target.isActive()) {
                target.enqueue(out);
            } else {
                writerExecutor.execute(() -> offlineStore.save(to, from, text, id, ts));
            }
            s.enqueue(out);
            metrics.incDms();
        } catch (ProtocolValidator.ProtocolException e) {
            error(s, "9", "invalid dm");
        }
    }

    private void handleListRooms(Session s, Message m) {
        if (!s.isActive()) return;
        StringBuilder sb = new StringBuilder();
        for (Room r : rm.list()) sb.append(r.name).append(" ");
        s.enqueue(new Message(MessageType.ROOMS, Map.of("rooms", sb.toString().trim())));
    }

    private void handleListUsers(Session s, Message m) {
        if (!s.isActive()) return;
        StringBuilder sb = new StringBuilder();
        for (Session ss : sm.all()) if (ss.username != null) sb.append(ss.username).append(" ");
        s.enqueue(new Message(MessageType.USERS, Map.of("users", sb.toString().trim())));
    }

    private void handleHistory(Session s, Message m) {
        if (!s.isActive()) return;
        try {
            String room = ProtocolValidator.room(m.fields());
            String before = m.get("before");
            String limit = m.get("limit");
            int n = config.historyLimit;
            if (limit != null) { try { n = Integer.parseInt(limit); if (n < 1 || n > 200) n = config.historyLimit; } catch (NumberFormatException ignored) {} }
            List<String> hist;
            if (before != null) { hist = historyStore.before(room, Long.parseLong(before), n); }
            else { hist = historyStore.lastN(room, n); }
            for (String entry : hist) {
                String[] parts = entry.split(":", 4);
                if (parts.length < 4) continue;
                s.enqueue(new Message(MessageType.HISTORY, Map.of("room", room, "id", parts[0], "from", parts[1], "text", parts[2], "ts", parts[3])));
            }
        } catch (ProtocolValidator.ProtocolException | NumberFormatException e) {
            error(s, "9", "invalid history request");
        }
    }

    private void handlePresence(Session s, Message m) {
        if (!s.isActive()) return;
        String status = m.get("status");
        if (status == null) status = "ONLINE";
        String user = s.username;
        for (String room : new java.util.ArrayList<>(s.rooms)) {
            Room r = rm.get(room);
            if (r != null) r.broadcast(sm, new Message(MessageType.PRESENCE, Map.of("user", user, "status", status, "room", room)), s);
        }
    }

    private void handleTyping(Session s, Message m) {
        if (!s.isActive()) return;
        String room = m.get("room");
        if (room == null || !s.rooms.contains(room)) return;
        String key = "typing:" + s.username;
        long last = typingLast.getOrDefault(key, 0L);
        if (now() - last < config.typingThrottleMs) return;
        typingLast.put(key, now());
        Room r = rm.get(room);
        if (r != null) r.broadcast(sm, new Message(MessageType.TYPING, Map.of("user", s.username, "room", room)), s);
    }
    private final java.util.concurrent.ConcurrentHashMap<String, Long> typingLast = new java.util.concurrent.ConcurrentHashMap<>();

    private void handleModeration(Session s, Message m) {
        if (!s.isActive()) return;
        try {
            String room = ProtocolValidator.room(m.fields());
            String target = m.get("target");
            Room r = rm.get(room);
            if (r == null) { error(s, "3", "no such room"); return; }
            boolean isMod = r.isModerator(s.username);
            switch (m.type()) {
                case KICK -> {
                    if (target == null || target.isEmpty()) { error(s, "9", "missing target"); return; }
                    if (!isMod || !r.canModerate(s.username, target)) {
                        error(s, "8", "permission denied"); return;
                    }
                    Session victim = sm.byUsername(target);
                    if (victim != null) { victim.enqueue(new Message(MessageType.KICK, Map.of("room", room))); victim.close(); }
                    r.removeMember(target);
                    writerExecutor.execute(() -> roomStore.removeMember(room, target));
                    s.enqueue(new Message(MessageType.NOTICE, Map.of("msg","kicked " + target)));
                }
                case BAN -> {
                    if (target == null || target.isEmpty()) { error(s, "9", "missing target"); return; }
                    if (!isMod || !r.canModerate(s.username, target)) { error(s, "8", "permission denied"); return; }
                    r.banned.add(target);
                    writerExecutor.execute(() -> roomStore.banUser(room, target));
                    Session victim = sm.byUsername(target);
                    if (victim != null) { victim.enqueue(new Message(MessageType.BAN, Map.of("room", room))); victim.close(); }
                    r.removeMember(target);
                    s.enqueue(new Message(MessageType.NOTICE, Map.of("msg","banned " + target)));
                }
                case MUTE -> {
                    if (target == null || target.isEmpty()) { error(s, "9", "missing target"); return; }
                    if (!isMod || !r.canModerate(s.username, target)) { error(s, "8", "permission denied"); return; }
                    r.muted.add(target);
                    writerExecutor.execute(() -> roomStore.muteUser(room, target));
                    s.enqueue(new Message(MessageType.NOTICE, Map.of("msg","muted " + target)));
                }
                case TOPIC -> {
                    if (!isMod) { error(s, "8", "permission denied"); return; }
                    String topic = m.get("topic");
                    r.topic = topic == null ? "" : topic;
                    writerExecutor.execute(() -> roomStore.upsertRoom(room, r.creator, r.topic, r.locked, r.persistent));
                    r.broadcast(sm, new Message(MessageType.TOPIC, Map.of("room", room, "topic", r.topic, "by", s.username)), s);
                }
                case LOCK -> {
                    if (!isMod) { error(s, "8", "permission denied"); return; }
                    r.locked = !r.locked;
                    writerExecutor.execute(() -> roomStore.upsertRoom(room, r.creator, r.topic, r.locked, r.persistent));
                    r.broadcast(sm, new Message(MessageType.LOCK, Map.of("room", room, "locked", String.valueOf(r.locked), "by", s.username)), s);
                }
                default -> error(s, "10", "unknown moderation command");
            }
        } catch (ProtocolValidator.ProtocolException e) {
            error(s, "9", "invalid room");
        }
    }

    private void handleAdmin(Session s, Message m) {
        if (!s.isActive()) return;
        String cmd = m.get("cmd");
        if ("listusers".equals(cmd)) {
            StringBuilder sb = new StringBuilder();
            for (Session ss : sm.all()) if (ss.username != null) sb.append(ss.username).append(" ");
            s.enqueue(new Message(MessageType.NOTICE, Map.of("msg", "users: " + sb)));
        } else if ("sessions".equals(cmd)) {
            s.enqueue(new Message(MessageType.NOTICE, Map.of("msg", "sessions: " + sm.count())));
        } else if ("rooms".equals(cmd)) {
            StringBuilder sb = new StringBuilder();
            for (Room r : rm.list()) sb.append(r.name).append("(").append(r.memberCount()).append(") ");
            s.enqueue(new Message(MessageType.NOTICE, Map.of("msg", "rooms: " + sb)));
        } else if ("stats".equals(cmd)) {
            s.enqueue(new Message(MessageType.NOTICE, Map.of(
                "msg", String.format("messages=%d dms=%d authfail=%d drops=%d joins=%d leaves=%d sessions=%d qdepth=%d",
                    metrics.messages(), metrics.dms(), metrics.authFailures(), metrics.drops(),
                    metrics.joins(), metrics.leaves(), metrics.sessions(), metrics.queueDepth()))));
        } else if ("force-disconnect".equals(cmd)) {
            String target = m.get("target");
            Session victim = sm.byUsername(target);
            if (victim != null) { victim.close(); s.enqueue(new Message(MessageType.NOTICE, Map.of("msg","disconnected " + target))); }
            else error(s, "3", "no such user");
        } else {
            error(s, "10", "unknown admin command");
        }
    }

    private void error(Session s, String code, String msg) {
        s.enqueue(new Message(MessageType.ERROR, Map.of("code", code, "msg", msg)));
    }
}
