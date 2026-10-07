# Contributing

Agentic WebView is in breaking development. Changes should strengthen the typed architecture instead of restoring prototype compatibility.

## Local checks

Use JDK 17, Android SDK 37, and the Node version in `.nvmrc`.

```bash
./gradlew deterministicCheck
```

Gradle installs `web-runtime/` dependencies from `package-lock.json`, type-checks and tests the runtime, validates architecture and documentation links, runs module tests and lint, builds every artifact, and enforces artifact-size budgets. Individual steps are available as `checkWebRuntime`, `buildWebRuntime`, `validateArchitecture`, `validateDocLinks`, and `checkArtifactSizes`.

Deterministic tests are required for pull requests. Emulator and live-agent tests must remain separate from deterministic SDK correctness checks.

## Dependency direction

- `browser-api` is pure Kotlin and cannot depend on Android UI or an agent framework.
- `browser-webview` implements `browser-api` and owns all WebView internals.
- `browser-compose` only adapts host ownership and lifecycle.
- `agent-tools` depends on public contracts, never the Android implementation.
- framework adapters only translate definitions, JSON arguments, and results.
- `web-runtime` communicates through the versioned protocol only.

## Change rules

- Preserve document/frame scoping and structured error behavior.
- Register runtime requests before dispatch and terminate each request exactly once.
- Treat webpage text as untrusted data; do not interpolate it into authority-bearing prompts.
- Add fixture coverage for frames, Shadow DOM, controlled inputs, stale references, and protocol failures when relevant.
- Modify TypeScript source and rebuild; never edit `agentic_runtime.min.js` directly.
- Do not add an external dependency without prior maintainer agreement.
- Update canonical Markdown docs in `docs/`; the website should link to those sources rather than fork examples.

## Pull requests

Explain the public behavior, failure modes, tests, security impact, and any protocol or artifact-size change. Protocol-breaking changes require a version bump and updated fixtures under `protocol-fixtures/`.
