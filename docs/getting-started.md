# Getting started

Agentic WebView is a typed, framework-neutral browser SDK for Android. It transforms an Android `WebView` into an autonomous agent-governed browsing environment with semantic page observations, stable element references, verified action dispatch, and deterministic lifecycle control.

## System requirements

- Android API 28+ (Android 9.0 Pie or newer)
- Java 17 bytecode (JDK 17+ toolchain)
- Kotlin 2.0+ with Coroutines support
- Android app host capable of rendering an Android `WebView`

## Dependency setup

Agentic WebView is modularized into six published artifacts on Maven Central under the group ID `dev.shantoislam.agenticwebview`. Add only the dependencies your application requires:

```kotlin
// build.gradle.kts
dependencies {
    // Core contracts and data models (Pure Kotlin / JVM)
    implementation("dev.shantoislam.agenticwebview:browser-api:0.3.1")

    // Android WebView host implementation, lifecycle & runtime bridge (Android AAR)
    implementation("dev.shantoislam.agenticwebview:browser-webview:0.3.1")

    // Optional: Jetpack Compose integration (Android AAR)
    implementation("dev.shantoislam.agenticwebview:browser-compose:0.3.1")

    // Optional: Framework-neutral LLM agent tool definitions & dispatcher (JVM)
    implementation("dev.shantoislam.agenticwebview:agent-tools:0.3.1")

    // Optional: Official Koog agent framework binding (JVM)
    implementation("dev.shantoislam.agenticwebview:integration-koog:0.3.1")

    // Optional: Transport-neutral JSON-RPC 2.0 tool server (JVM)
    implementation("dev.shantoislam.agenticwebview:integration-jsonrpc:0.3.1")
}
```

Declare internet access in your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

> [!NOTE]
> Navigation enforces HTTPS by default. If you need to navigate to local HTTP test fixtures or cleartext domains, configure `NavigationPolicy(allowedSchemes = setOf("https", "http"))` and ensure your `networkSecurityConfig.xml` allows cleartext traffic for those domains. See [Security](security.md) and [Configuration](configuration.md) for details.

---

## Creating a browser host & session

An `AgenticBrowserHost` owns the underlying `WebView` and provides access to its governing `AgenticBrowserSession`. Host instantiation must take place on the Android main thread.

### Option A: SDK-owned WebView (Recommended)
```kotlin
import dev.shantoislam.agenticwebview.api.AgenticBrowserConfiguration
import dev.shantoislam.agenticwebview.webview.AgenticBrowserHost
import dev.shantoislam.agenticwebview.webview.BrowserHostDelegate

// Create a host that owns and creates its WebView
val host = AgenticBrowserHost.create(
    context = activityContext,
    configuration = AgenticBrowserConfiguration(),
    delegate = BrowserHostDelegate.DenyAll, // Or provide a custom delegate
)

// Add host.view to your Android View hierarchy
containerLayout.addView(host.view)
```

### Option B: Attaching to an existing WebView
```kotlin
// Attach to an existing, pre-configured WebView without taking destruction ownership
val host = AgenticBrowserHost.attach(
    webView = existingWebView,
    configuration = AgenticBrowserConfiguration(),
    delegate = BrowserHostDelegate.DenyAll,
)
```

Always release the host when its hosting UI lifecycle ends:

```kotlin
override fun onDestroy() {
    host.close() // Cancels pending requests and releases resources
    super.onDestroy()
}
```

---

## End-to-end API recipe: Navigate, Observe, and Click

All session operations (`navigate`, `observe`, `execute`, `await`, `navigateHistory`) are suspending functions returning typed `BrowserResult<T>`. Expected browser errors (e.g., stale elements, navigation failures) are returned as values (`BrowserResult.Failure`) rather than thrown as exceptions.

Here is a complete, compile-ready example executing inside a lifecycle coroutine scope:

```kotlin
import androidx.lifecycle.lifecycleScope
import dev.shantoislam.agenticwebview.api.BrowserCommand
import dev.shantoislam.agenticwebview.api.BrowserError
import dev.shantoislam.agenticwebview.api.BrowserResult
import dev.shantoislam.agenticwebview.api.NavigationRequest
import dev.shantoislam.agenticwebview.api.ObservationOptions
import dev.shantoislam.agenticwebview.api.PageNodeKind
import dev.shantoislam.agenticwebview.api.WaitCondition
import kotlinx.coroutines.launch

lifecycleScope.launch {
    // 1. Navigate to a target URL and await page readiness
    val navRequest = NavigationRequest(
        url = "https://example.com",
        readiness = WaitCondition.PageReady,
    )
    
    when (val navResult = host.session.navigate(navRequest)) {
        is BrowserResult.Success -> {
            println("Navigated to: ${navResult.value.finalUrl} (Doc ID: ${navResult.value.documentId.value})")
        }
        is BrowserResult.Failure -> {
            when (val error = navResult.error) {
                is BrowserError.NavigationBlocked -> println("Navigation blocked: ${error.url} (${error.reason})")
                is BrowserError.NavigationFailed -> println("Navigation failed: HTTP ${error.httpStatus}")
                else -> println("Navigation error: ${error.message}")
            }
            return@launch // Stop if navigation failed
        }
    }

    // 2. Observe the webpage semantic tree
    val obsOptions = ObservationOptions(
        includeScreenshot = false,
        includeCompactText = true,
        includeOffscreenContent = false,
    )

    when (val observation = host.session.observe(obsOptions)) {
        is BrowserResult.Success -> {
            println("Page title: ${observation.value.document.title}")
            println("Observation Revision: ${observation.value.revision.value}")
            println("Compact text preview:\n${observation.value.compactText}")

            // 3. Locate an actionable element using semantic nodes
            val targetNode = observation.value.nodes.firstOrNull { node ->
                node.elementRef != null && (
                    node.accessibleName?.contains("More information", ignoreCase = true) == true ||
                    node.role == "button"
                )
            }

            val targetElementRef = targetNode?.elementRef
            if (targetElementRef == null) {
                println("No actionable target element found on page.")
                return@launch
            }

            // 4. Execute a typed command (Click)
            when (val clickResult = host.session.execute(BrowserCommand.Click(target = targetElementRef))) {
                is BrowserResult.Success -> {
                    val receipt = clickResult.value
                    println("Action executed using strategy: ${receipt.strategy}")
                    println("Verified effect: ${receipt.verified}, Page changed: ${receipt.pageChanged}")
                }
                is BrowserResult.Failure -> {
                    when (val error = clickResult.error) {
                        is BrowserError.StaleElementReference -> {
                            println("Element reference was stale! Re-observe before retrying.")
                        }
                        is BrowserError.ElementOccluded -> {
                            println("Element is occluded by another element: ${error.occludedBy}")
                        }
                        is BrowserError.ActionNotVerified -> {
                            println("Action dispatched but produced no observable DOM effect: ${error.message}")
                        }
                        else -> println("Click failed: ${error.message}")
                    }
                }
            }
        }
        is BrowserResult.Failure -> {
            println("Failed to observe webpage: ${observation.error.message}")
            return@launch
        }
    }
}
```

---

## Next steps

- Explore [Android Views integration](views.md) for Activity/Fragment setups and custom host delegates.
- Explore [Jetpack Compose integration](compose.md) for reactive UI state observation.
- Read the [Command reference](commands.md) for typing, keyboard chords, dropdown selection, and scrolling.
- Learn about the [Observation model](observations.md) and [Frames & Shadow DOM](frames-shadow-dom.md).
- Configure timeouts, policies, and screenshots in [Configuration guide](configuration.md).
- Understand error handling and recovery in [Diagnostics and troubleshooting](diagnostics.md).
- Monitor session phases and events in [Lifecycle and cancellation](lifecycle.md).
- Connect LLMs using [Framework-neutral agent tools](agent-tools.md), [Koog integration](koog.md), or [JSON-RPC](jsonrpc.md).
