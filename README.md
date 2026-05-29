<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="logo-dark.svg" />
    <img src="logo.svg" width="128" height="128" alt="Agentic WebView Logo" />
  </picture>
</p>

# Agentic WebView SDK

An Android SDK library that gives LLM-powered AI agents real web-browsing capabilities inside mobile apps. The SDK bridges the gap between large language models and Android WebViews by injecting scripts to parse and simplify the DOM into an accessibility tree, capturing viewport screenshots for vision models, and translating LLM tool calls into native Android touch interactions.

## Features

-   **Simplified Accessibility Tree**: Converts complex HTML into a clean, LLM-friendly JSON tree of interactive elements with **stable identifiers** that persist across mutations.
-   **Shadow DOM & Iframe Support**: Recursively traverses Shadow DOM and same-origin iframes.
-   **Occlusion Detection**: Automatically identifies if elements are visible or hidden behind overlays/modals.
-   **Framework-Safe Interactions**: Simulated inputs that work reliably with React, Vue, and Angular event systems.
-   **Hardware-Accelerated Screenshots**: Uses `PixelCopy` to capture high-quality viewport snapshots, including videos and WebGL content.
-   **Reactive State Management**: Exposes a `StateFlow` for live tracking of DOM mutations and page state changes.
-   **Jetpack Compose Ready**: Includes a native Compose wrapper for modern Android development.
-   **Anti-Detection Mode**: Hides WebDriver flags and automation signals to prevent bot detection.
-   **CSS Selector Generation**: Automatically generates robust CSS selectors for each element.
-   **Element Stability Detection**: Waits for element positions to stabilize before performing interactions.
-   **Loading Progress Tracking**: Exposes a `StateFlow<Int>` for real-time page loading progress (0-100).

## Architecture

The SDK is built in two layers:

1.  **TypeScript DOM Engine (`web-injector/`)**: A single IIFE bundle injected into pages that handles DOM parsing, interactivity detection, coordinate math, and framework-safe input simulation. Exposes `window.__AgenticInternal`.
2.  **Kotlin SDK Core (`agentic-webview/`)**: The Android library containing the custom WebView, `AgenticWebController` orchestrator, `JsBridge` (`@JavascriptInterface`), `ScreenshotCapture`, and Compose integration.

Communication flows via `evaluateJavascript` (Kotlin → JS) and `@JavascriptInterface` callbacks (JS → Kotlin), secured by a per-navigation session UUID token.

## Setup

### 1. Requirements
-   Android API 28+ (Android 9.0)
-   Kotlin 2.x
-   Compose (optional)

### 2. Dependency
Add the following to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.shantoislam:agentic-webview:0.2.0")
}
```

> **Note:** Make sure `mavenCentral()` is in your `dependencyResolutionManagement.repositories` block in `settings.gradle.kts`.

The library includes Jetpack Compose dependencies. Use `AgenticWebViewComposable` for Compose integration, or `AgenticWebView` directly for View-based layouts.

## Usage

### Jetpack Compose Integration

```kotlin
val controller = remember { AgenticWebController() }

AgenticWebViewComposable(
    controller = controller,
    modifier = Modifier.fillMaxSize(),
    config = AgenticWebViewConfig(enableDebugLogging = true)
)

// In a Coroutine scope
controller.state.collect { state ->
    state?.let { 
        // React to live DOM updates or page settlement
        println("New state: ${it.url}")
    }
}

val result = controller.executeAction(AgentAction.Navigate("https://google.com"))
if (result is AgentResult.Success) {
    val stateResult = controller.captureState()
    if (stateResult is AgentResult.Success) {
        val state = stateResult.data
        // Send state.compactTree and state.screenshotBase64 to your LLM
    }
}
```

### Agent Actions
The SDK supports 20 browsing actions:

**Navigation:** `Navigate(url)`, `GoBack`, `GoForward`, `Refresh`, `Wait(durationMs)`

**Interaction:** `Click(agentId)`, `LongPress(agentId, durationMs)`, `InputText(agentId, text, clearFirst)`, `SelectOption(agentId, value)`, `SendKeys(keys)`

**Scrolling:** `Scroll(direction, amount: Float)`, `ScrollToPercent(yPercent, agentId?)`, `ScrollToText(text, nth)`, `ScrollToTop(agentId?)`, `ScrollToBottom(agentId?)`, `PreviousPage(agentId?)`, `NextPage(agentId?)`

**Dropdowns:** `GetDropdownOptions(agentId)`, `SelectDropdownOption(agentId, text)`

**Completion:** `Done(text, success)`

## Testing

The SDK includes a comprehensive instrumented test suite using `MockWebServer`.

```bash
./gradlew :agentic-webview:connectedAndroidTest
```

## Documentation

For comprehensive guides and API references, see the following:

-   [Getting Started](docs/getting-started.md): Installation and basic setup (Views and Compose).
-   [Agent Perception & Actions](docs/agent-perception.md): How to capture state and execute actions.
-   [Best Practices](docs/best-practices.md): Stability, error handling, and debugging.
-   `AGENTS.md`: Machine-readable instructions and context for AI coding agents.

## License

This project is licensed under the Apache License 2.0.
