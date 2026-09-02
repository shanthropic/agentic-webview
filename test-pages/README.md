# Deterministic browser fixtures

Run `node test-pages/server/server.mjs` and open `http://127.0.0.1:8787/complex.html`. These pages contain no external resources and exercise controlled inputs, mutation identity, nested same-origin frames, open Shadow DOM, scrolling, privacy redaction, and action verification.

They are intended for Android instrumentation and real-browser runtime tests. Unit tests keep smaller inline fixtures near the tested source.
