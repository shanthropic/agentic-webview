# Contributing to Agentic WebView SDK

Thank you for your interest in contributing to the **Agentic WebView SDK**! We are building the perception and interaction layers that empower LLM-powered AI agents to browse the web natively inside mobile apps.

By contributing, you help make web-agent integration in Android apps more robust, reliable, and secure.

---

## Code of Conduct

This project and everyone participating in it is governed by our [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to **[contact@shantoislam.dev](mailto:contact@shantoislam.dev)**.

---

## How Can I Contribute?

There are many ways to contribute, including:
- **Reporting Bugs**: Let us know about incorrect DOM parsing, occlusion detection issues, or bridge crashes.
- **Suggesting Enhancements**: Propose new `AgentAction` types, configuration parameters, or performance improvements.
- **Improving Documentation**: Fix typos, add integration examples, or expand best practices.
- **Contributing Code**: Fix open issues or implement approved new features in either the Kotlin SDK or TypeScript injector.

---

## Project Structure

The project is structured into two main layers:

1. **`agentic-webview/` (Kotlin Android Library)**: The core SDK, custom WebView components, `AgenticWebController`, `@JavascriptInterface` bridge, and Jetpack Compose bindings.
2. **`web-injector/` (TypeScript DOM Engine)**: Bundled into an IIFE and injected into web pages. It handles Shadow DOM/iframe traversal, interactive element detection, coordinate math, stability checks, and native-like input simulation.
3. **`app/` (Sample Demo)**: A demonstration application showing how to integrate and test the SDK.
4. **`docs/`**: Integration and agent perception guides.

---

## Development Setup & Workflow

### 1. Prerequisites
- **Android SDK**: API 28+ (Android 9.0) is the minimum required version.
- **Kotlin**: 2.x+
- **Node.js & npm**: Required to build the TypeScript injector.
- **Android Emulator or Physical Device**: For executing instrumented tests.

---

### 2. Developing the TypeScript Injector (`web-injector/`)

The TypeScript code compiles into a minified JavaScript bundle, which the Android SDK loads as an asset.

> [!WARNING]
> Never modify `agentic-webview/src/main/assets/agentic_core.min.js` directly. This file is generated. Always modify the TypeScript files under `web-injector/src/` and build them.

#### Setup & Build
1. Navigate to the `web-injector` directory:
   ```bash
   cd web-injector
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Build the minified injector bundle:
   ```bash
   npm run build
   ```
   This will bundle `src/index.ts` and output it to `dist/agentic_core.min.js` (which is copied/synchronized into the Android assets during compilation).

#### Testing TypeScript
The TS DOM engine uses Jest for tests. Run the test suite:
```bash
npm test
```

---

### 3. Developing the Android Library (`agentic-webview/`)

#### Build & Lint
To build the Android project and run code style checks (Lint):
- **Build Full Project**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Lint Code**:
  ```bash
  ./gradlew lint
  ```

#### Running Tests
The Android library features comprehensive instrumented tests using `MockWebServer` to test DOM parsing, screenshots, actions, and JS-to-Kotlin callbacks.
- **Run Android Tests** (requires an emulator or device connected and unlocked):
  ```bash
  ./gradlew :agentic-webview:connectedAndroidTest
  ```

---

## Coding Standards

### Thread Safety
- The public API of the `AgenticWebController` operates concurrently. All state captures and action executions MUST be serialized using the internal `Mutex` to avoid race conditions.

### The Async JS-to-Kotlin Bridge
- When introducing asynchronous behavior in `web-injector` (e.g. waiting for elements, scroll animations), utilize the custom `resolvePromise` architecture. This allows the Kotlin coroutine to suspend and await completion/error feedback from the JavaScript engine explicitly without polling.

### Compatibility
- To avoid breaking interactive pages built with reactive frameworks (React, Vue, Angular, etc.), simulate input interactions by triggering native prototype setters and dispatching appropriate synthetic input, change, and keyboard events.

---

## Pull Request Guidelines

1. **Create a Topic Branch**: Use descriptive names like `feature/add-scroll-keys` or `bugfix/occlusion-iframe`.
2. **Write Meaningful Commits**: Use clear, concise commit messages (Conventional Commits are highly appreciated, e.g., `feat(injector): add support for custom touch boundaries`).
3. **Keep PRs Focused**: Avoid massive, multi-purpose PRs. Submit separate, small PRs for distinct features or fixes.
4. **Update Tests**:
   - If you modify the DOM parser, add Jest tests in `web-injector/`.
   - If you add or modify interactions or controller logic, add instrumented tests in `agentic-webview/src/androidTest/`.
5. **Run Pre-Commit Checks**: Ensure that `npm run build && npm test` and `./gradlew lint` pass cleanly before submitting.
6. **Update Documentation**: If your change modifies an API or adds a new action/configuration, update the relevant files in `docs/` or `README.md`.

---

## Reporting Issues

When reporting an issue, please include:
1. **SDK Version**: (e.g., `0.2.0`)
2. **Android OS version & Device Model**.
3. **Step-by-step reproduction instructions**, and if possible, a sample URL or HTML snippet that reproduces the issue.
4. **Relevant Logcat output**: Enable debug logging with `.setEnableDebugLogging(true)` and capture the logs under the `AgenticSDK` tag pattern.

---

Thank you again for helping to build the future of agentic web interaction! If you have any questions or need guidance, reach out to the maintainers at **[contact@shantoislam.dev](mailto:contact@shantoislam.dev)**.
