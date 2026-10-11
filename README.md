# Agentic WebView

Agentic WebView is a typed, framework-neutral browser SDK for Android. It transforms an Android `WebView` into an autonomous agent-governed browsing environment with semantic page observations, document-scoped element references, verified action execution, navigation security policies, screenshots, and structured error diagnostics.

---

## Published Modules

| Module | Format | Description |
| --- | --- | --- |
| `dev.shantoislam.agenticwebview:browser-api` | JAR | Platform-neutral contracts, data models, configuration, commands, observations, errors, and events |
| `dev.shantoislam.agenticwebview:browser-webview` | AAR | Android WebView host implementation, lifecycle state machine, policy interceptors, and bundled TypeScript page runtime |
| `dev.shantoislam.agenticwebview:browser-compose` | AAR | Jetpack Compose UI bindings (`rememberAgenticBrowserHost`, `AgenticBrowserView`) |
| `dev.shantoislam.agenticwebview:agent-tools` | JAR | Framework-neutral JSON Schema tool definitions, dispatcher, and envelope serializer |
| `dev.shantoislam.agenticwebview:integration-koog` | JAR | Reflective `ToolSet` adapter for the JetBrains Koog Agents library |
| `dev.shantoislam.agenticwebview:integration-jsonrpc` | JAR | Transport-neutral JSON-RPC 2.0 tool endpoint for remote execution over HTTP, WebSocket, or stdio |

---

## Installation

Add only the modules required by your Android app or agent service:

```kotlin
// build.gradle.kts
dependencies {
    // 1. Core contracts and data models (Pure Kotlin / JVM)
    implementation("dev.shantoislam.agenticwebview:browser-api:0.3.0")

    // 2. Android Views WebView host (Android AAR)
    implementation("dev.shantoislam.agenticwebview:browser-webview:0.3.0")

    // 3. Jetpack Compose integration (Android AAR, optional)
    implementation("dev.shantoislam.agenticwebview:browser-compose:0.3.0")

    // 4. Framework-neutral agent tool definitions & dispatcher (JVM, optional)
    implementation("dev.shantoislam.agenticwebview:agent-tools:0.3.0")

    // 5. Koog Agent framework binding (JVM, optional)
    implementation("dev.shantoislam.agenticwebview:integration-koog:0.3.0")

    // 6. JSON-RPC 2.0 tool server (JVM, optional)
    implementation("dev.shantoislam.agenticwebview:integration-jsonrpc:0.3.0")
}
```

---

## Quick Start (Jetpack Compose)

```kotlin
@Composable
fun AgenticBrowserApp() {
    val host = rememberAgenticBrowserHost(
        configuration = AgenticBrowserConfiguration(
            navigation = NavigationPolicy(
                allowedHosts = setOf(HostRule.DomainAndSubdomains("example.com")),
            ),
        ),
    )

    LaunchedEffect(host) {
        when (val result = host.session.navigate(NavigationRequest("https://example.com"))) {
            is BrowserResult.Success -> println("Navigated to: ${result.value.finalUrl}")
            is BrowserResult.Failure -> println("Navigation error: ${result.error.message}")
        }
    }

    AgenticBrowserView(
        host = host,
        modifier = Modifier.fillMaxSize(),
    )
}
```

---

## Documentation Index

Explore the dedicated API usage and architecture guides:

| Guide | Description |
| --- | --- |
| [Getting started](docs/getting-started.md) | Setup requirements, installation, and end-to-end navigate/observe/click recipe |
| [Android Views integration](docs/views.md) | `AgenticBrowserHost`, custom `BrowserHostDelegate`, Activity lifecycle, and event collection |
| [Jetpack Compose integration](docs/compose.md) | `rememberAgenticBrowserHost`, `AgenticBrowserView`, reactive `StateFlow` collection |
| [Command reference](docs/commands.md) | Detailed recipes for all 7 commands, native touch simulation, `await`, and `navigateHistory` |
| [Configuration cookbook](docs/configuration.md) | Comprehensive reference for all 7 sub-configurations with real-world recipes |
| [Observation model](docs/observations.md) | Semantic DOM extraction, `ElementRef` identity, redaction, and observation metrics |
| [Diagnostics & error catalog](docs/diagnostics.md) | Catalog of all 23 `BrowserError` types and recovery recipes; Kotlin vs JSON tool codes |
| [Lifecycle & event streams](docs/lifecycle.md) | Detailed reference for the 11 session phases and 11 browser event types |
| [Framework-neutral agent tools](docs/agent-tools.md) | Schemas for all 12 tools, profiles (Minimal, Standard, Advanced), and JSON output envelope |
| [Koog agent integration](docs/koog.md) | Step-by-step recipe for building autonomous agents using JetBrains Koog |
| [JSON-RPC tool endpoint](docs/jsonrpc.md) | Transport-neutral JSON-RPC 2.0 tool discovery and invocation over WebSocket/HTTP |
| [Sample application guide](docs/samples.md) | Architecture and runner guide for `samples/android` (Offline Simulation & Autonomous Agent) |
| [Security & navigation policies](docs/security.md) | Host allowlisting, scheme restrictions, SSL enforcement, and privileged request delegation |
| [Privacy & prompt injection](docs/privacy-prompt-injection.md) | Threat boundaries, untrusted webpage isolation, and sensitive data redaction |
| [Frames & Shadow DOM](docs/frames-shadow-dom.md) | Nested frames, origin isolation boundaries, and open/closed Shadow DOM traversal |
| [Testing strategy](docs/testing.md) | Test fixtures, unit verification, and deterministic check gates |
| [Release process](docs/release.md) | Published Maven artifacts, byte budgets, and release workflow checklist |
| [Architecture overview](docs/architecture.md) | Module dependency graph and core architectural guarantees |

---

## Core Guarantees

- **Single Owner**: One `AgenticBrowserSession` owns one WebView, one configuration, and one deterministic lifecycle.
- **Correlated Envelopes**: All JavaScript communication uses correlated, versioned protocol envelopes with timeout, payload size, and pending-request limits.
- **Scrolled & Verified Actions**: Actions validate document identity, enabled state, visibility, and occlusion; commands verify postconditions before returning success.
- **Native Touch Simulation**: Optional Android hardware-like pointer synthesis (`ANDROID_NATIVE_POINTER`) creates trusted touch events for complex gesture-sensitive webapps.
- **Untrusted Page Content**: Page content is marked untrusted; sensitive fields (`password`, `data-agentic-sensitive`) are automatically redacted.
- **No Direct LLM Dependency in Core**: The browser core is framework-neutral. Agent tool adapters sit above the pure browser session.

---

## Development Checks

```bash
# Run all deterministic verification checks
./gradlew deterministicCheck
```

Gradle installs `web-runtime/` dependencies automatically and builds `web-runtime/dist/agentic_runtime.min.js`. Never edit the bundled runtime directly.

---

## License

Apache License 2.0. See [LICENSE](LICENSE).
