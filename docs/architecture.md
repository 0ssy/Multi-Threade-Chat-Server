# Architecture

## Modules
- protocol: messages, framing, encoding/decoding (no networking)
- server: acceptor, session manager, room manager, router, auth, persistence
- client: connection handling, input parsing, rendering, reconnect
- tools: load generator, fuzzer, benchmark scripts

## Concurrency
- Thread-per-connection baseline with bounded outbound queues
- All socket writes via session writer thread only
- ConcurrentHashMap for registries; rooms use concurrent member sets

## Backpressure
- Bounded LinkedBlockingQueue per session; on full disconnect slow client

## Components
- ServerSocket acceptor
- SessionManager (ConcurrentHashMap)
- RoomManager (ConcurrentHashMap)
- MessageRouter
- UserStore / PasswordHasher
- Persistence (H2 via JDBC)
