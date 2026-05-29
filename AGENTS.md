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
- **Run TS Tests**: `cd web-injector && npm test`

## Project Structure
- `agentic-webview/`: Core Android library.
  - `src/main/java/`: Kotlin implementation of the custom WebView, Controller, and models.
  - `src/main/assets/`: Contains the bundled `agentic_core.min.js` (do not edit directly).
  - `src/androidTest/`: Comprehensive instrumented test suite using MockWebServer.
  - `src/test/`: Unit tests.
  - `config/`: `AgenticWebViewConfig` data class with `Builder` pattern.
  - `internal/`: Internal helpers — `JsEvaluator.kt`, `JsUtils.kt`, `ScreenshotCapture.kt`, `SdkLogger.kt`.
  - `models/`: `AgentAction.kt`, `AgentResult.kt`, `AgentState.kt`, `DropdownOption.kt`, `PageLifecycleState.kt`.
- `web-injector/`: TypeScript project for in-page DOM parsing and interaction.
  - `src/index.ts`: Main entry point — creates `AgenticEngine`, wires up all modules, sets up anti-detection, mutation observers, and iframe bus. Exports `window.__AgenticInternal`.
  - `src/buildDomTree.ts`: Core DOM tree engine. WeakMap caching, cursor-based interactive detection, isTopElement, XPath generation, shadow DOM traversal.
  - `src/domParser.ts`: Wrapper around BuildDomTreeEngine. Produces `AccessibilityNode[]` with highlightIndex, xpath, isTopElement, isInteractive. Also defines the `AccessibilityNode` TypeScript interface.
  - `src/serializer.ts`: LLM-optimized `[index]<tag attrs>text />` compact text format with token dedup.
  - `src/cssSelector.ts`: `enhancedCssSelectorForElement()` — XPath-to-CSS + class names + safe attributes.
  - `src/elementHash.ts`: SHA-256 element identity for cross-mutation element matching.
  - `src/stability.ts`: Element position stability polling (getBoundingClientRect until delta < 2px).
  - `src/iframeBus.ts`: `postMessage` relay between subframes and main frame.
  - `src/interaction.ts`: Framework-safe input simulation, sendKeys, scroll variants, dropdown handling.
  - `src/bridge.ts`: JS-to-Kotlin bridge (`@JavascriptInterface` wrapper).
- `app/`: Sample/demo application module.
- `docs/`: User-facing documentation (`getting-started.md`, `agent-perception.md`, `best-practices.md`).

## AgentAction Types
- `Click(agentId)`, `LongPress(agentId, durationMs)`, `InputText(agentId, text, clearFirst)`
- `SelectOption(agentId, value)`, `Scroll(direction: ScrollDirection, amount: Float = 0.5f)`, `Navigate(url)`
- `GoBack`, `GoForward`, `Refresh`, `Wait(durationMs)`
- `SendKeys(keys)` — Keyboard shortcuts (e.g., `"Control+A"`, `"Enter"`)
- `ScrollToPercent(yPercent, agentId?)` — Scroll to percentage position
- `ScrollToText(text, nth)` — Find visible text and scroll to it
- `ScrollToTop(agentId?)`, `ScrollToBottom(agentId?)` — Scroll to extremes
- `PreviousPage(agentId?)`, `NextPage(agentId?)` — Scroll by viewport height
- `GetDropdownOptions(agentId)` — Enumerate `<select>` options (result stored in `lastDropdownOptions`)
- `SelectDropdownOption(agentId, text)` — Select option by matching text
- `Done(text, success)` — Signal task completion

## Public API (AgenticWebController)

### State Capture
- `captureState(): AgentResult<AgentState>` — Returns accessibility tree, screenshot, viewport, selectorMap, compactTree
- `state: StateFlow<AgentState?>` — Reactive state updates
- `getDropdownOptions(agentId): AgentResult<List<DropdownOption>>` — Typed dropdown enumeration
- `loadingProgress: StateFlow<Int>` — Reactive page loading progress (0-100)

### Action Execution
- `executeAction(action: AgentAction): AgentResult<Unit>` — Execute any action with retry logic
- `lastDropdownOptions: String?` — Raw JSON from last GetDropdownOptions call

### Navigation Queries
- `canGoBack(): Boolean` — Check if back navigation is available
- `canGoForward(): Boolean` — Check if forward navigation is available

### Lifecycle
- `attach(webView: AgenticWebView)` — Bind controller to a WebView instance
- `detach()` — Unbind controller from the WebView
- `destroy()` — Cancel scope, release resources, cancel pending promises
- `pauseTimers()` / `resumeTimers()` — WebView timer lifecycle management

## Key Models

### AgentResult<T>
- `AgentResult.Success<T>(val data: T)` — Successful operation with typed data
- `AgentResult.Error(val error: AgentError)` — Failed operation with typed error

### AgentError (sealed class — 11 error types)
- `JsEvaluationTimeout(timeoutMs)`, `JsEvaluationFailed(message)`
- `ElementNotFound(agentId)`, `ElementOccluded(agentId, occludedBy)`
- `NavigationFailed(url, httpCode)`, `WebViewCrashed(didRecover)`
- `ScreenshotFailed(reason)`, `PageNotReady(currentState)`
- `Timeout(operation, timeoutMs)`, `FileUploaderDetected(agentId)`
- `NoNavigationHistory(direction)`

### PageLifecycleState (enum)
`IDLE` → `LOADING` → `INTERACTIVE` → `COMPLETE` | `ERROR` | `CRASHED`

### ScrollDirection (enum)
`UP`, `DOWN`, `LEFT`, `RIGHT`

### DropdownOption (data class)
`value: String`, `text: String`, `index: Int`

## AgentState Fields
- `accessibilityTree: String` — JSON array of `AccessibilityNode`
- `screenshotBase64: String?` — JPEG screenshot
- `viewportInfo: ViewportInfo` — DPR, scale, scroll, dimensions
- `url: String`, `title: String`, `pageState: PageLifecycleState`
- `elementCount: Int`, `truncated: Boolean`
- `selectorMap: Map<String, String>?` — highlightIndex → agentId mapping
- `compactTree: String?` — LLM-optimized text format

## AccessibilityNode Fields
Note: `AccessibilityNode` is a TypeScript interface defined in `web-injector/src/domParser.ts`, serialized as JSON.
- `id`, `tag`, `text`, `role`, `bounds: ElementBounds`, `attributes`, `occluded`, `inIframe`
- `xpath: String` — Relative XPath from nearest boundary
- `isTopElement: Boolean` — Topmost at position via elementFromPoint
- `isInteractive: Boolean` — Cursor/tag/role-based detection
- `highlightIndex: Int?` — LLM reference index
- `cssSelector: String` — Generated CSS selector for the element
- `isNew: Boolean` — True if element is new or changed since last capture (hash-based diffing)
- `depth: Int` — DOM tree depth of the node

## AgenticWebView Static API
- `AgenticWebView.init()` — No-op initializer (reserved for future use)
- `AgenticWebView.getVersion(): String` — Returns SDK version string (e.g., `"0.2.1"`)

## AgenticWebViewListener (callback interface)
- `onStateChanged(state: PageLifecycleState)` — Lifecycle state transition
- `onProgressChanged(progress: Int)` — Loading progress 0-100
- `onDomMutated(json: String)` — DOM mutation detected
- `onPromiseResolved(promiseId: String, result: String)` — JS promise resolved
- `onCrash(didRecover: Boolean)` — Renderer crash notification
- `onNewTabRequested(url: String)` — `window.open()` popup intercepted (default no-op)

## Config Options (AgenticWebViewConfig)
- `jsEvaluationTimeoutMs`, `pageSettleTimeoutMs`, `pageSettleDebounceMs`
- `screenshotEnabled`, `screenshotQuality`, `screenshotMaxDimension`
- `maxDomElements`, `domMutationThrottleMs`, `actionRetryCount`
- `enableDebugLogging`, `userAgent`, `allowedHosts`, `deniedHosts`, `homeUrl`
- `viewportExpansion: Int` — px to expand viewport; -1 = all visible
- `elementStabilityTimeoutMs: Long` — Wait for element stability
- `enableAntiDetection: Boolean` — Hide webdriver, force open shadow DOM
- `includeAttributes: List<String>?` — Attributes for serializer
- All options also available via `AgenticWebViewConfig.Builder` fluent API

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
- `docs/getting-started.md`: Installation and basic setup (Views and Compose).
- `docs/agent-perception.md`: How to capture state and execute actions.
- `docs/best-practices.md`: Stability, error handling, and debugging.
