# Design Decisions

- Framing: 4-byte length-prefixed binary payload. Safer than newline for arbitrary content and enforces size limits before allocation.
- Outbound: LinkedBlockingQueue per session (bounded). Full queue => disconnect slow client (keep others responsive).
- Registries: ConcurrentHashMap; room delete via compute atomic.
- Duplicate usernames: putIfAbsent atomic.
- Session close: atomic state transition ensures single cleanup.
- Passwords: BCrypt hashed, never logged.
- DB writes: (future) dedicated executor to avoid stalling reader threads.
- Delivery: at-least-once with server-assigned IDs; client dedupes by ID.
