# Threat Model

- Flooding: per-user token-bucket rate limiter (planned).
- Slowloris: socket read timeouts + idle eviction.
- Oversized frames: length check before allocation; MAX_FRAME.
- Username spoofing: auth required for active; server sets identity.
- Connection exhaustion: global connection cap + per-IP limit (planned).
- Replay: session tokens + TLS (planned).
