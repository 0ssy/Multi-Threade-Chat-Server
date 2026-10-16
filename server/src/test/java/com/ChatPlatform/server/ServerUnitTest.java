package com.ChatPlatform.server;

import com.ChatPlatform.server.config.Config;
import com.ChatPlatform.server.auth.FailedLoginTracker;
import com.ChatPlatform.server.rate.RateLimiterRegistry;
import com.ChatPlatform.server.store.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ServerUnitTest {

    @Test
    void inMemoryUserStore() {
        UserStore s = new InMemoryUserStore();
        assertTrue(s.register("alice", "h1"));
        assertFalse(s.register("alice", "h2"));
        assertEquals("h1", s.getHash("alice"));
        assertNull(s.getHash("bob"));
        assertTrue(s.exists("alice"));
        assertFalse(s.exists("bob"));
    }

    @Test
    void inMemoryHistoryOrdering() {
        HistoryStore s = new InMemoryHistoryStore();
        s.save("r", "a", "m1", 1, 100);
        s.save("r", "b", "m2", 2, 200);
        s.save("r", "c", "m3", 3, 300);
        List<String> last = s.lastN("r", 2);
        assertEquals(2, last.size());
        assertTrue(last.get(1).startsWith("3:"));
        List<String> before = s.before("r", 3, 10);
        assertEquals(2, before.size());
    }

    @Test
    void offlineMessageStoreRoundtrip() {
        OfflineMessageStore s = new InMemoryOfflineMessageStore();
        s.save("bob", "alice", "hi", 1, 100);
        s.save("bob", "carol", "hello", 2, 200);
        List<String> q = s.takeOffline("bob");
        assertEquals(2, q.size());
        assertTrue(s.takeOffline("bob").isEmpty());
    }

    @Test
    void roomRolesAndPermissions() {
        Room r = new Room("r", "owner", false);
        assertTrue(r.isOwner("owner"));
        r.addMember("mem", Room.Role.MEMBER);
        assertFalse(r.canModerate("mem", "owner"));
        assertTrue(r.canModerate("owner", "mem"));
        r.addMember("mod", Room.Role.MODERATOR);
        assertTrue(r.canModerate("mod", "mem"));
        assertFalse(r.canModerate("mod", "owner"));
        assertFalse(r.canModerate("mem", "mod"));
        assertEquals(3, r.memberCount());
    }

    @Test
    void roomBanMute() {
        Room r = new Room("r", "owner", false);
        r.addMember("mem", Room.Role.MEMBER);
        r.muted.add("mem");
        assertTrue(r.muted.contains("mem"));
        r.muted.remove("mem");
        assertFalse(r.muted.contains("mem"));
        r.banned.add("mem");
        assertTrue(r.banned.contains("mem"));
        assertTrue(r.isMember("mem"));
    }

    @Test
    void rateLimiterRegistry() {
        RateLimiterRegistry rl = new RateLimiterRegistry(3, 1000);
        assertTrue(rl.tryAcquire("u"));
        assertTrue(rl.tryAcquire("u"));
        assertTrue(rl.tryAcquire("u"));
        assertFalse(rl.tryAcquire("u"));
        assertFalse(rl.tryAcquire("u"));
        assertTrue(rl.tryAcquire("other"));
    }

    @Test
    void failedLoginTracker() {
        FailedLoginTracker f = new FailedLoginTracker(2, 1000);
        assertFalse(f.isBlocked("u"));
        f.recordFailure("u");
        f.recordFailure("u");
        assertTrue(f.isBlocked("u"));
        f.reset("u");
        assertFalse(f.isBlocked("u"));
    }

    @RepeatedTest(50)
    void concurrentRoomJoinRepeated() throws Exception {
        RoomManager rm = new RoomManager();
        AtomicInteger joins = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(8);
        ExecutorService ex = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 8; i++) {
            final int idx = i;
            ex.execute(() -> {
                try {
                    Room r = rm.getOrCreate("shared", "u" + idx);
                    r.addMember("u" + idx, Room.Role.MEMBER);
                    joins.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        ex.shutdown();
        ex.awaitTermination(5, TimeUnit.SECONDS);
        assertEquals(8, joins.get());
        Room r = rm.get("shared");
        assertEquals(8, r.memberCount());
    }

    @Test
    void configDefaults() {
        Config c = new Config();
        assertEquals(5000, c.port);
        assertEquals(Config.StoreType.MEMORY, c.storeType);
    }
}
