# Best Practices & Troubleshooting

Tips for building robust and reliable web agents using the Agentic WebView SDK.

## 1. Page Lifecycle States

Understanding the `PageLifecycleState` is key to knowing when it's safe to interact with the page.

| State | Description |
| :--- | :--- |
| `LOADING` | Page is currently loading. |
| `INTERACTIVE` | DOM is ready, but sub-resources (images, scripts) might still be loading. |
| `COMPLETE` | Page is fully settled and stable. **Capture state now.** |
| `ERROR` | A navigation error occurred (e.g., 404, DNS). |
| `CRASHED` | The WebView renderer process crashed. |

```kotlin
controller.state.collect { state ->
    if (state?.pageState == PageLifecycleState.COMPLETE) {
        // Safe to capture state or execute actions
    }
}
```

## 2. Comprehensive Error Handling

The SDK uses `AgentResult` to provide detailed feedback on why an operation failed.

| Error Type | Description |
| :--- | :--- |
| `ElementNotFound` | The requested `agentId` does not exist in the current DOM. |
| `ElementOccluded` | The element is covered by a modal, overlay, or another element. |
| `NavigationFailed` | Page failed to load. Includes the `httpCode` if available. |
| `JsEvaluationFailed` | The internal JS engine failed (check logs for details). |
| `FileUploaderDetected`| Clicking a file input is blocked (requires native file picker). |
| `NoNavigationHistory`| `GoBack` or `GoForward` called when there's no history. |
| `Timeout` | The operation (navigation or stability) exceeded the config timeout. |
| `WebViewCrashed` | The renderer process died. |

Example:
```kotlin
when (val result = controller.executeAction(action)) {
    is AgentResult.Success -> { /* Proceed */ }
    is AgentResult.Error -> {
        val error = result.error
        if (error is AgentError.ElementOccluded) {
            println("Element ${error.agentId} is hidden by ${error.occludedBy}")
        }
    }
}
```

## 3. Debugging

### Enable SDK Logging
Set `enableDebugLogging` to `true` in your config to see detailed logs in Logcat under the `AgenticSDK` tag.

```kotlin
val config = AgenticWebViewConfig.Builder()
    .setEnableDebugLogging(true)
    .build()
```

### Chrome Remote Debugging
Since the SDK uses a standard `WebView`, you can inspect the page (including the injected scripts) using Chrome DevTools.

In your Activity/Application:
```kotlin
if (BuildConfig.DEBUG) {
    WebView.setWebContentsDebuggingEnabled(true)
}
```
Then open `chrome://inspect` in your desktop browser.

## 4. Performance Tips

- **Limit DOM Nodes**: Use `maxDomElements` to keep the accessibility tree small and save LLM tokens.
- **Viewport Expansion**: Avoid setting `viewportExpansion = -1` (full page) unless absolutely necessary, as it can significantly slow down state capture on long pages.
