# Testing

Run deterministic checks:

```bash
./gradlew deterministicCheck
```

This single task installs `web-runtime/` dependencies (`npm ci`), runs `checkWebRuntime` (type-check and Jest), `buildWebRuntime`, `validateArchitecture`, `validateDocLinks`, every module's `check` (unit tests and lint), builds all publishable artifacts and the sample APK, and finishes with `checkArtifactSizes`. CI and the release workflow run the same task.

TypeScript tests cover protocol validation, semantic extraction, identity stability, frames, Shadow DOM, redaction, input strategies, and action outcomes. Kotlin tests cover configuration, lifecycle reduction, policy matching, request correlation/cancellation, and tool adapters. Both sides consume `protocol-fixtures/v1`.

Android instrumentation should use local deterministic pages for PixelCopy, real WebView navigation, coordinate transformation, controlled inputs, renderer recovery, and privileged callbacks. Live LLM tests are optional and must not determine SDK correctness.

`validate-architecture.mjs` enforces the module graph, pure-Kotlin boundaries, deleted prototype paths, aligned Gradle/runtime versions, and the private runtime bundle budget. `check-artifact-sizes.mjs --require-built` fails when a publishable artifact is absent or exceeds its checked-in size budget.
