# Repository instructions

## Tooling

- Android/Kotlin: `./gradlew`
- Page runtime: `npm` in `web-runtime/`
- Runtime check: `npm run check`
- Architecture check: `node scripts/validate-architecture.mjs`
- Documentation check: `node scripts/validate-doc-links.mjs`
- Full deterministic check: `./gradlew check :browser-api:jar :agent-tools:jar :integrations:jsonrpc:jar :integrations:koog:jar :browser-webview:assembleRelease :browser-compose:assembleRelease :samples:android:assembleDebug`
- Built artifact budgets: `node scripts/check-artifact-sizes.mjs --require-built`

## Architecture boundaries

- Keep public platform-neutral contracts in `browser-api`.
- Keep Android and WebView implementation details in `browser-webview`.
- Keep Compose ownership/lifecycle only in `browser-compose`.
- Keep framework-neutral schemas and dispatch in `agent-tools`.
- Keep framework-specific translation in `integrations/`.
- Keep page-side behavior behind the versioned protocol in `web-runtime`.

One `AgenticBrowserSession` owns one configuration and lifecycle. Do not introduce a second controller/configuration path or any compatibility façade for the deleted prototype API.

## Runtime invariants

- Requests are registered before JavaScript dispatch.
- Requests have bounded size, timeout, and pending-count limits.
- Navigation and renderer termination invalidate document-bound work.
- Element references include document, frame, element, and observed revision.
- Same-origin frames may be actionable; cross-origin frames must report explicit limitations.
- Text input uses native prototype setters and emits framework-compatible events.
- Page content is untrusted and sensitive values must remain redacted.

Always run `npm run check` after TypeScript changes. Never edit generated runtime bundles directly. Ask before adding external dependencies.
