# Diagnostics and error handling

All errors in Agentic WebView are returned as structured data in `BrowserResult.Failure(val error: BrowserError, val diagnostics: OperationDiagnostics)`.

---

## Kotlin API vs Agent Tools Error Handling

> [!IMPORTANT]
> **Key Architectural Distinction**:
> - **Kotlin SDK Consumers**: In Kotlin code, `BrowserError` is a sealed interface with typed subclasses. Pattern match against subclasses using `when (val error = result.error)` to access strongly-typed properties (e.g. `error.target`, `error.occludedBy`, `error.phase`, `error.httpStatus`).
> - **LLM Agent Tools (JSON)**: In JSON tool envelopes emitted by `agent-tools` (`StandardBrowserTools`) and JSON-RPC, errors serialize a stable uppercase string field `"code"` (such as `"STALE_ELEMENT"`, `"PAGE_NOT_READY"`, `"ELEMENT_OCCLUDED"`, `"NAVIGATION_BLOCKED"`).

### Idiomatic Kotlin pattern matching:

```kotlin
when (val result = host.session.execute(command)) {
    is BrowserResult.Success -> {
        println("Command succeeded: ${result.value}")
    }
    is BrowserResult.Failure -> {
        // Pattern match against typed sealed subclasses
        when (val error = result.error) {
            is BrowserError.StaleElementReference -> {
                // Recovery: Re-observe the page to obtain fresh element references
                val freshObservation = host.session.observe()
            }
            is BrowserError.ElementOccluded -> {
                // Recovery: Scroll element into viewport center before retrying
                host.session.execute(BrowserCommand.ScrollIntoView(error.target))
            }
            is BrowserError.PageNotReady -> {
                // Recovery: Await page readiness
                host.session.await(WaitCondition.PageReady)
            }
            is BrowserError.NavigationBlocked -> {
                println("Navigation blocked: ${error.url} (${error.reason})")
            }
            is BrowserError.Timeout -> {
                println("Operation ${error.operation} timed out after ${error.timeoutMs}ms")
            }
            is BrowserError.RendererTerminated -> {
                // Fatal: Renderer crashed or killed by OS; host must be recreated
                println("Renderer terminated (crash=${error.didCrash}). Recreating host...")
            }
            else -> {
                println("Unrecoverable error: ${error.message}")
            }
        }
    }
}
```

---

## Complete Catalog of all 23 BrowserError Variants

| # | Error Subclass | Properties | Root Cause & Recovery Strategy |
|---|---|---|---|
| 1 | `BrowserError.SessionNotAttached` | None | Session was called before attaching to a WebView or after WebView was unparented. Ensure host is attached to window. |
| 2 | `BrowserError.SessionClosed` | None | Session was closed via `host.close()`. Recreate host/session to resume browsing. |
| 3 | `BrowserError.NavigationBlocked` | `val url: String`, `val reason: String` | Blocked by `NavigationPolicy` (disallowed scheme, host not allowed, or explicit deny rule). Check `NavigationPolicy.allowedHosts`. |
| 4 | `BrowserError.NavigationFailed` | `val url: String`, `val httpStatus: Int?` | Network failure, DNS resolution failure, or HTTP error status. Check connectivity or HTTP status code. |
| 5 | `BrowserError.NavigationHistoryUnavailable` | `val operation: NavigationOperation` | History `BACK` or `FORWARD` requested but WebView history stack has no such entry (`canGoBack()`/`canGoForward()` was false). |
| 6 | `BrowserError.PageNotReady` | `val phase: BrowserSessionPhase` | Action attempted while session is still `NAVIGATING` or `RUNTIME_INITIALIZING`. Await `WaitCondition.PageReady` before acting. |
| 7 | `BrowserError.StaleElementReference` | `val target: ElementRef` | The target element was detached, replaced, or DOM mutated since the last observation. **Recovery**: Call `session.observe()` and re-query the element. |
| 8 | `BrowserError.ElementNotFound` | `val target: ElementRef` | Target element ID does not exist in the active document. Call `session.observe()` to obtain fresh references. |
| 9 | `BrowserError.ElementNotActionable` | `val target: ElementRef`, `val reason: String` | Element is `disabled`, `readonly`, or invisible (`display: none`, zero size). Check node states before dispatching commands. |
| 10 | `BrowserError.ElementOccluded` | `val target: ElementRef`, `val occludedBy: ElementRef?` | Another element covers the target element at its pointer hit-test point. **Recovery**: Call `BrowserCommand.ScrollIntoView` or dismiss overlays. |
| 11 | `BrowserError.UnsupportedFrame` | `val frameId: FrameId`, `val reason: String` | Frame is cross-origin, sandboxed without `allow-scripts`, or detached. Action cannot cross the origin boundary. |
| 12 | `BrowserError.UnsupportedAction` | `val action: String`, `val reason: String` | Action is not supported on this node type (e.g. attempting `SelectOption` on a `<div>`). |
| 13 | `BrowserError.ActionRejected` | `val action: String`, `val reason: String` | Action rejected by page constraints (e.g. text input rejected by pattern restriction or non-editable node). |
| 14 | `BrowserError.ActionNotVerified` | `val action: String`, `val receipt: CommandReceipt?` | Command dispatched successfully, but produced no detectable DOM change, focus change, or navigation. May need alternative interaction strategy. |
| 15 | `BrowserError.RuntimeUnavailable` | None | TypeScript runtime script is not responding or failed to inject. Session may be navigating or document not initialized. |
| 16 | `BrowserError.RuntimeProtocolMismatch` | `val expected: Int`, `val actual: Int?` | The bundled runtime JavaScript version does not match the Kotlin protocol version. Rebuild project using `./gradlew buildWebRuntime`. |
| 17 | `BrowserError.RuntimeFailure` | `val code: String`, `val details: Map<String, String>` | In-page JavaScript execution error thrown by the runtime bridge. Check details map for stack traces or error names. |
| 18 | `BrowserError.MalformedRuntimeResponse` | None | JSON payload returned across JavaScript bridge was corrupt or failed schema validation. |
| 19 | `BrowserError.RendererTerminated` | `val didCrash: Boolean` | Android WebView renderer process was killed due to low memory or browser crash. Session is invalidated; recreate host. |
| 20 | `BrowserError.ScreenshotFailure` | None | PixelCopy or hardware drawing buffer capture failed. Check if WebView is hardware-accelerated and currently attached to a window. |
| 21 | `BrowserError.Timeout` | `val operation: String`, `val timeoutMs: Long` | Operation exceeded configured timeout limit (`requestTimeoutMs` or `initializationTimeoutMs`). |
| 22 | `BrowserError.Cancelled` | `val operation: String` | Operation was cancelled because a new navigation started or caller coroutine was cancelled. |
| 23 | `BrowserError.ResourceLimitExceeded` | `val resource: String`, `val limit: Long` | Payload or message exceeded safety thresholds (e.g., maximum pending requests or maximum message byte size). |

---

## Configuring diagnostics & telemetry sinks

You can capture detailed internal telemetry without leaking page content:

```kotlin
// 1. Configure diagnostics in browser configuration
val configuration = AgenticBrowserConfiguration(
    diagnostics = DiagnosticsConfiguration(
        enabled = true,
        includePageContent = false, // Keep false in production to avoid leaking sensitive DOM strings
    ),
)

// 2. Implement BrowserDiagnosticsSink
val diagnosticsSink = BrowserDiagnosticsSink { event ->
    println("Category: ${event.category}")
    println("Operation ID: ${event.operationId}")
    println("Duration: ${event.durationMs}ms")
    println("Document: ${event.documentId?.value}, Revision: ${event.revision?.value}")
    event.attributes.forEach { (key, value) ->
        println("  Attribute [$key] = $value")
    }
}

// 3. Attach sink during host creation
val host = AgenticBrowserHost.create(
    context = context,
    configuration = configuration,
    diagnosticsSink = diagnosticsSink,
)
```

---

## Related documentation

- [Command reference](commands.md)
- [Configuration guide](configuration.md)
- [Lifecycle & event stream](lifecycle.md)
- [Framework-neutral agent tools](agent-tools.md)
