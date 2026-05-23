# Agentic WebView SDK

An Android SDK library that gives LLM-powered AI agents real web-browsing capabilities inside mobile apps. The SDK bridges the gap between large language models and Android WebViews by injecting scripts to parse and simplify the DOM into an accessibility tree, capturing viewport screenshots for vision models, and translating LLM tool calls into native Android touch interactions.

## Features

-   **Simplified Accessibility Tree**: Converts complex HTML into a clean, LLM-friendly JSON tree of interactive elements.
-   **Shadow DOM & Iframe Support**: Recursively traverses Shadow DOM and same-origin iframes.
-   **Occlusion Detection**: Automatically identifies if elements are visible or hidden behind overlays/modals.
-   **Framework-Safe Interactions**: Simulated inputs that work reliably with React, Vue, and Angular event systems.
-   **Screenshot Capturing**: Provides high-quality viewport snapshots for multimodal LLMs.
-   **Jetpack Compose Ready**: Includes a native Compose wrapper for modern Android development.
-   **Robust Error Handling**: Structured result types for programmatic failure handling (timeouts, missing elements, etc.).

## Architecture

The SDK is built in three layers:

1.  **TypeScript DOM Engine (`web-injector/`)**: A bundled script injected into pages that handles DOM parsing and coordinate math.
2.  **Kotlin SDK Core (`agentic-webview/`)**: The Android library containing the custom WebView, orchestrator, and Compose integration.
3.  **Action Pipeline**: A mutex-serialized pipeline that translates high-level agent actions into native Android touch events.

## Setup

### 1. Requirements
-   Android API 28+ (Android 9.0)
-   Kotlin 2.x
-   Compose (optional)

### 2. Dependency
Add the following to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":agentic-webview"))
}
```

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
val result = controller.executeAction(AgentAction.Navigate("https://google.com"))
if (result is AgentResult.Success) {
    val state = controller.captureState()
    // Send state.accessibilityTree and state.screenshotBase64 to your LLM
}
```

### Agent Actions
The SDK supports a wide range of browsing actions:
-   `Click(agentId)`
-   `InputText(agentId, text)`
-   `Scroll(direction, amount)`
-   `Navigate(url)`
-   `GoBack`, `GoForward`, `Refresh`, `Wait`

## Testing

The SDK includes a comprehensive instrumented test suite using `MockWebServer`.

```bash
./gradlew :agentic-webview:connectedAndroidTest
```

## Documentation

-   `AGENTS.md`: Machine-readable instructions and context for AI coding agents.
-   `docs/AGENT_GUIDE.md`: Comprehensive guide for developers building web agents using this SDK.
-   `walkthrough.artifact.md`: Technical implementation details and verification summary.

## License

This project is licensed under the MIT License.
