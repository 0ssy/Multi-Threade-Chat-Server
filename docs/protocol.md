# Protocol Specification

## Transport
TCP, one persistent connection per client, length-prefixed framing.

## Framing
4-byte big-endian length (max 1<<20 bytes) followed by UTF-8 JSON payload.
Length-prefixed chosen over newline-delimited so messages can contain newlines and size limits are trivial.

## Message Types
HELLO, AUTH, JOIN, LEAVE, MSG, DM, LIST_ROOMS, LIST_USERS, PING, PONG, ERROR, NOTICE, HISTORY.

## Limits
- Max frame: 1,048,576 bytes
- Max username: 32 chars
- Max room name: 32 chars
- Max message: 4,096 chars

## Error Codes
1 BAD_FRAME, 2 NOT_AUTHENTICATED, 3 NO_SUCH_ROOM, 4 RATE_LIMITED, 5 AUTH_FAILED, 6 VERSION_MISMATCH, 7 ALREADY_EXISTS, 8 PERMISSION_DENIED, 9 OVERSIZE, 10 SERVER_ERROR

## Handshake
Client sends HELLO{version}; server replies HELLO or ERROR(version mismatch).
