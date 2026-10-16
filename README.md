# MultiThreadedChatServer

A production-grade, multi-threaded TCP chat server platform with an H2-backed persistence layer, rate limiting, moderation, room management, and optional TLS.

## Modules

| Module | Purpose |
|--------|---------|
| `protocol` | Wire protocol: `Message` record, `MessageType` enum, `FrameCodec` length-preframed codec, `ProtocolValidator` |
| `server` | TCP server: `ServerMain`, `SessionManager`, `RoomManager`, `MessageRouter`, auth/rate-limit/fail-login tracking, metrics, H2 + in-memory stores |
| `client` | `ClientConnection`, `TerminalClient` (CLI), `GuiClient` (Swing) |
| `tools` | `LoadGenerator` (load test), `Fuzzer` (protocol fuzz survival) |

## Build

Requires Java 21+ and Maven 3.9+.

```bash
./mvnw -q install
```

Or run a single module:

```bash
./mvnw -q -pl server install
```

Tests:

```bash
./mvnw -q test
```

## Running the server

```bash
java -cp server/target/server-1.0.0-shaded.jar com.ChatPlatform.server.ServerMain 5000
```

`ServerMain` accepts an optional port argument (default `5000`).

### Configuration

Create a `chatserver.properties` file and pass its path as the second argument:

```
port=5000
max.connections=1024
max.connections.per.ip=16
history.limit=50
typing.throttle.ms=1500
rate.limit.capacity=30
failed.login.cap=5
store.type=MEMORY        # MEMORY or H2
db.path=data/chatdb
tls.enabled=false
```

TLS support: set `tls.enabled=true` and provide `keystore.jks` on the classpath (server `src/main/resources`). A dev self-signed keystore (`CN=localhost`, SAN `localhost`/`127.0.0.1`, password `changeit`) is included under `server/src/main/resources/keystore.jks`.

## Clients

Terminal client (plain TCP):

```bash
java -cp client/target/classes:protocol/target/classes com.ChatPlatform.client.TerminalClient localhost 5000
```

GUI client (Swing):

```bash
java -cp client/target/classes:protocol/target/classes com.ChatPlatform.client.GuiClient localhost 5000
```

## Tools

Load generator:

```bash
java -cp tools/target/classes:protocol/target/classes com.ChatPlatform.tools.LoadGenerator <connections> <host> <port> <seconds>
```

Fuzzer (protocol survival against a live server):

```bash
java -cp tools/target/classes:protocol/target/classes com.ChatPlatform.tools.Fuzzer <connections> <host> <port> <seconds>
```

## Architecture

- **ServerMain** — bootstrap, SSL/TLS server socket, connection caps (global + per-IP), idle eviction scheduler, shutdown hook.
- **Session** — per-connection reader/writer threads, FrameCodec framing, auth state, graceful close with drain.
- **SessionManager / RoomManager** — concurrent session and room registries.
- **MessageRouter** — auth (`LOGIN`/`REGISTER`), rooms (`JOIN`/`LEAVE`/`SEND`), moderation (`MUTE`/`UNMUTE`/`KICK`/`BAN`), admin (`LISTUSERS`/`LISTROOMS`/`SHUTDOWN`), whisper (`WHISPER`).
- **Stores** — `UserStore`, `HistoryStore`, `OfflineMessageStore`, `RoomStore` with H2 and in-memory implementations.
- **RateLimiterRegistry / FailedLoginTracker** — token-bucket rate limiting and failed-login backoff.
- **Metrics** — connection/message/error counters.

## Testing

64 server tests (58 unit + 6 integration) all pass. Run with:

```bash
./mvnw -q -pl server test
```

## License

MIT

## Quick start

```bash
# Build everything
./mvnw -q install

# Start the server on port 5000
java -cp server/target/server-1.0.0-shaded.jar com.ChatPlatform.server.ServerMain 5000

# In another terminal, connect a chat client
java -cp client/target/classes:protocol/target/classes:server/target/classes com.ChatPlatform.client.TerminalClient localhost 5000
```

No screenshot is included yet — add one at `docs/screenshot.png` and reference it as `![screenshot](docs/screenshot.png)` if you'd like one in the README.
