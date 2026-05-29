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
  - `src/androidTest/`: Comprehensive test suite using MockWebServer.
- `web-injector/`: TypeScript project for in-page DOM parsing and interaction.
  - `src/buildDomTree.ts`: Core DOM tree engine. WeakMap caching, cursor-based interactive detection, isTopElement, XPath generation, shadow DOM traversal.
  - `src/domParser.ts`: Wrapper around BuildDomTreeEngine. Produces `AccessibilityNode[]` with highlightIndex, xpath, isTopElement, isInteractive.
  - `src/serializer.ts`: LLM-optimized `[index]<tag attrs>text />` compact text format with token dedup.
  - `src/cssSelector.ts`: `enhancedCssSelectorForElement()` — XPath-to-CSS + class names + safe attributes.
  - `src/elementHash.ts`: SHA-256 element identity for cross-mutation element matching.
  - `src/stability.ts`: Element position stability polling (getBoundingClientRect until delta < 2px).
  - `src/iframeBus.ts`: `postMessage` relay between subframes and main frame.
  - `src/interaction.ts`: Framework-safe input simulation, sendKeys, scroll variants, dropdown handling.
  - `src/bridge.ts`: JS-to-Kotlin bridge (`@JavascriptInterface` wrapper).

## AgentAction Types
- `Click(agentId)`, `LongPress(agentId, durationMs)`, `InputText(agentId, text, clearFirst)`
- `SelectOption(agentId, value)`, `Scroll(direction, amount)`, `Navigate(url)`
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

### Action Execution
- `executeAction(action: AgentAction): AgentResult<Unit>` — Execute any action with retry logic
- `lastDropdownOptions: String?` — Raw JSON from last GetDropdownOptions call

## AgentState Fields
- `accessibilityTree: String` — JSON array of `AccessibilityNode`
- `screenshotBase64: String?` — JPEG screenshot
- `viewportInfo: ViewportInfo` — DPR, scale, scroll, dimensions
- `url: String`, `title: String`, `pageState: PageLifecycleState`
- `elementCount: Int`, `truncated: Boolean`
- `selectorMap: Map<String, String>?` — highlightIndex → agentId mapping
- `compactTree: String?` — LLM-optimized text format

## AccessibilityNode Fields
- `id`, `tag`, `text`, `role`, `bounds`, `attributes`, `occluded`, `inIframe`
- `xpath: String` — Relative XPath from nearest boundary
- `isTopElement: Boolean` — Topmost at position via elementFromPoint
- `isInteractive: Boolean` — Cursor/tag/role-based detection
- `highlightIndex: Int?` — LLM reference index

## Config Options (AgenticWebViewConfig)
- `jsEvaluationTimeoutMs`, `pageSettleTimeoutMs`, `pageSettleDebounceMs`
- `screenshotEnabled`, `screenshotQuality`, `screenshotMaxDimension`
- `maxDomElements`, `domMutationThrottleMs`, `actionRetryCount`
- `enableDebugLogging`, `userAgent`, `allowedHosts`, `deniedHosts`, `homeUrl`
- `viewportExpansion: Int` — px to expand viewport; -1 = all visible
- `elementStabilityTimeoutMs: Long` — Wait for element stability
- `enableAntiDetection: Boolean` — Hide webdriver, force open shadow DOM
- `includeAttributes: List<String>?` — Attributes for serializer

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
