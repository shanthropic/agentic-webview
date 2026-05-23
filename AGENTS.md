# AGENTS.md - Instructions for AI Coding Agents

## Project Context
- **Description**: Agentic WebView SDK - An Android library providing an accessibility-tree-based perception layer and native interaction pipeline for LLM-powered web agents.
- **Primary Stack**: Kotlin (Android SDK), TypeScript (DOM Injection), Gradle, npm, esbuild.
- **Package Manager**: Use `./gradlew` for Android/Kotlin and `npm` for the `web-injector` module.

## Essential Commands
- **Build Full Project**: `./gradlew assembleDebug`
- **Build Web Injector**: `cd web-injector && npm run build` (Generates `dist/agentic_core.min.js`)
- **Run Instrumented Tests**: `./gradlew :agentic-webview:connectedAndroidTest`
- **Lint Android**: `./gradlew lint`

## Project Structure
- `agentic-webview/`: Core Android library.
  - `src/main/java/`: Kotlin implementation of the custom WebView, Controller, and models.
  - `src/main/assets/`: Contains the bundled `agentic_core.min.js` (do not edit directly).
  - `src/androidTest/`: Comprehensive test suite using MockWebServer.
- `web-injector/`: TypeScript project for in-page DOM parsing and interaction.
  - `src/domParser.ts`: Accessibility tree generation logic.
  - `src/interaction.ts`: Framework-safe input simulation and coordinate math.

## Coding Standards
- **Thread Safety**: All `AgenticWebController` operations must be serialized via the internal `Mutex`.
- **Async Bridge**: Use the `resolvePromise` mechanism when implementing new asynchronous features in the `web-injector` to allow Kotlin to await JS completion.
- **Type Safety**: Use `AgentResult<T>` for all public SDK operations to handle errors explicitly.
- **Framework Compatibility**: When modifying `web-injector`, use native prototype setters for inputs to ensure compatibility with React/Vue/Angular synthetic event systems.
- **Compose**: Maintain Jetpack Compose support via `AgenticWebViewComposable`.

## Boundaries & Guardrails
- **✓ Always**: Run `npm run build` inside `web-injector` after modifying TypeScript files. The Gradle `bundleWebInjector` task should handle this during the build, but manual builds help catch TS errors early.
- **⚠️ Ask First**: Before adding new external dependencies to `agentic-webview` or `web-injector`.
- **✕ Never**: Modify `agentic_core.min.js` in assets directly; always modify the TypeScript source and rebuild.
- **✕ Never**: Expose `@JavascriptInterface` methods without appropriate thread-safe dispatch to the main looper.

## Documentation References
- `README.md`: General overview and setup for humans.
- `docs/AGENT_GUIDE.md`: Guide for developers *using* this SDK to build agents.
