# Release process & published artifacts

Agentic WebView publishes six modular artifacts to Maven Central under the group ID `dev.shantoislam.agenticwebview`.

## Published Maven Artifacts

| Coordinate | Format | Description | Size Budget |
| --- | --- | --- | --- |
| `dev.shantoislam.agenticwebview:browser-api` | JAR | Pure Kotlin/JVM contracts, data models, configuration, commands, observations, errors, and events. Zero Android dependencies. | 640 KB |
| `dev.shantoislam.agenticwebview:browser-webview` | AAR | Android WebView host implementation, lifecycle state machine, policy interceptors, PixelCopy screenshot capture, and bundled TypeScript page runtime. | 2.0 MB |
| `dev.shantoislam.agenticwebview:browser-compose` | AAR | Jetpack Compose bindings: `rememberAgenticBrowserHost` and `AgenticBrowserView`. | 1.0 MB |
| `dev.shantoislam.agenticwebview:agent-tools` | JAR | Framework-neutral JSON Schema tool definitions, dispatcher, and envelope serializer (`StandardBrowserTools`). | 512 KB |
| `dev.shantoislam.agenticwebview:integration-koog` | JAR | Reflective `ToolSet` adapter for the JetBrains Koog Agents library (`KoogBrowserTools`). | 512 KB |
| `dev.shantoislam.agenticwebview:integration-jsonrpc` | JAR | Transport-neutral JSON-RPC 2.0 endpoint (`JsonRpcBrowserToolServer`) for remote tool calling over HTTP, WebSocket, or stdio. | 512 KB |

*(In addition, the bundled in-page web runtime `agentic_runtime.min.js` has a strict size budget of 128 KB).*

---

## Release Verification Checklist

Every release must pass all deterministic checks before tagging:

```bash
# Execute the complete deterministic verification suite
./gradlew deterministicCheck
```

The `deterministicCheck` task enforces:
1. **Web Runtime Verification**: Installs pinned npm dependencies, runs Jest tests, type-checks TypeScript sources, and builds the minified bundle.
2. **Architecture Validation**: Executes `scripts/validate-architecture.mjs` to enforce module graph boundaries, purity of `browser-api`, absence of deprecated prototype APIs, and version alignment.
3. **Documentation Integrity**: Executes `scripts/validate-doc-links.mjs` to ensure all relative documentation links are valid.
4. **Unit & Lint Checks**: Runs `:check` on all Gradle subprojects.
5. **Artifact Assembly**: Compiles release JARs and AARs across all modules.
6. **Artifact Size Enforcement**: Executes `scripts/check-artifact-sizes.mjs --require-built` to verify every published artifact fits its byte budget.

---

## Release Steps

1. Update `VERSION_NAME` in `gradle.properties` and add release notes.
2. Ensure `web-runtime/package.json` base version aligns with `VERSION_NAME`.
3. Run `./gradlew deterministicCheck`.
4. Inspect Maven POMs, source/javadoc JARs, consumer ProGuard/R8 rules, and dependency graphs.
5. Tag the commit as `vVERSION` and push the tag.
6. The CI release workflow publishes all six artifacts to Maven Central and attaches the sample APK and SHA-256 checksum to the GitHub release.

---

## Related documentation

- [Testing strategy](testing.md)
- [Architecture reference](architecture.md)
- [Getting started](getting-started.md)
