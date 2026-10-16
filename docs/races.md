# Race Hall of Fame

1. Disconnect during broadcast: snapshot member iteration + enqueue no-op on closed session.
2. Join while room deleting: computeIfAbsent / compute atomic.
3. Duplicate username: putIfAbsent.
4. Double close: atomic state transition; cleanup once.
5. Message to closing session: guarded enqueue.
