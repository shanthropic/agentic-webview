# Agentic WebView

Agentic WebView is a typed, framework-neutral browser SDK for Android. It turns a `WebView` into an agent-controlled browser session with semantic observations, document-scoped element references, verified commands, navigation policy enforcement, screenshots, and structured failures.

This repository contains the breaking post-prototype architecture. There is intentionally no compatibility layer for the former controller API.

## Modules

| Module | Purpose |
| --- | --- |
| `browser-api` | Pure Kotlin contracts, configuration, observations, commands, errors, events, and diagnostics |
| `browser-webview` | Android WebView host, runtime gateway, lifecycle, policy hooks, and screenshots |
| `browser-compose` | Optional Compose view and lifecycle binding |
| `agent-tools` | Framework-neutral JSON Schema tool definitions and dispatcher |
| `integrations/koog` | Optional Koog `ToolSet` adapter |
| `integrations/jsonrpc` | Transport-neutral JSON-RPC 2.0 adapter |
| `web-runtime` | Strict TypeScript page runtime bundled into the Android library |
| `samples/android` | Direct SDK and generic-agent-tool example |

## Install

Use only the modules your app needs. Replace `VERSION` with a published development version.

```kotlin
dependencies {
    implementation("dev.shantoislam.agenticwebview:browser-webview:VERSION")
    implementation("dev.shantoislam.agenticwebview:browser-compose:VERSION") // optional
    implementation("dev.shantoislam.agenticwebview:agent-tools:VERSION") // optional
}
```

## Compose quick start

```kotlin
@Composable
fun Browser() {
    val host = rememberAgenticBrowserHost(
        AgenticBrowserConfiguration(
            navigation = NavigationPolicy(
                allowedHosts = setOf(HostRule.DomainAndSubdomains("example.com")),
            ),
        ),
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(host) {
        when (val result = host.session.navigate(NavigationRequest("https://example.com"))) {
            is BrowserResult.Success -> println(result.value)
            is BrowserResult.Failure -> println(result.error)
        }
    }

    AgenticBrowserView(host, Modifier.fillMaxSize())
}
```

For an agent framework, construct `StandardBrowserTools(host.session)`. Its standard profile exposes ten stable tools without giving an adapter access to `WebView` internals.

## Core guarantees

- A session owns one WebView, one configuration, and one deterministic lifecycle.
- All page communication uses correlated, versioned protocol envelopes with time, size, and pending-request limits.
- Element references are scoped to a document and frame; detached or replaced elements fail explicitly.
- Same-origin nested frames and open Shadow DOM are observed and acted on; cross-origin limits are reported as capabilities.
- Page observations are untrusted input. Password and explicitly sensitive content is redacted.
- Privileged WebView behavior is denied or delegated through host policy hooks.
- The browser core has no LLM or agent-framework dependency.

## Development checks

```bash
cd web-runtime
npm ci
npm run check
cd ..
node scripts/validate-architecture.mjs
node scripts/validate-doc-links.mjs
./gradlew check :browser-api:jar :agent-tools:jar :integrations:jsonrpc:jar :integrations:koog:jar :browser-webview:assembleRelease :browser-compose:assembleRelease :samples:android:assembleDebug
node scripts/check-artifact-sizes.mjs --require-built
```

The Gradle build always rebuilds `web-runtime/dist/agentic_runtime.min.js`; never edit the bundle directly.

See [Getting started](docs/getting-started.md), [Architecture](docs/architecture.md), [Security](docs/security.md), and the [full refactoring blueprint](docs/architecture-refactoring-plan.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
