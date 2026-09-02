# Testing

Run deterministic checks:

```bash
cd web-runtime && npm ci && npm run check
cd ..
node scripts/validate-architecture.mjs
node scripts/validate-doc-links.mjs
./gradlew check :browser-api:jar :agent-tools:jar :integrations:jsonrpc:jar :integrations:koog:jar :browser-webview:assembleRelease :browser-compose:assembleRelease :samples:android:assembleDebug
node scripts/check-artifact-sizes.mjs --require-built
```

TypeScript tests cover protocol validation, semantic extraction, identity stability, frames, Shadow DOM, redaction, input strategies, and action outcomes. Kotlin tests cover configuration, lifecycle reduction, policy matching, request correlation/cancellation, and tool adapters. Both sides consume `protocol-fixtures/v1`.

Android instrumentation should use local deterministic pages for PixelCopy, real WebView navigation, coordinate transformation, controlled inputs, renderer recovery, and privileged callbacks. Live LLM tests are optional and must not determine SDK correctness.

`validate-architecture.mjs` enforces the module graph, pure-Kotlin boundaries, deleted prototype paths, aligned Gradle/runtime versions, and the private runtime bundle budget. `check-artifact-sizes.mjs --require-built` fails when a publishable artifact is absent or exceeds its checked-in size budget.
