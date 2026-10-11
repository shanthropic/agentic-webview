# Android Views integration

The `browser-webview` artifact provides `AgenticBrowserHost`, which hosts an Android `WebView` and exposes an `AgenticBrowserSession`.

## Dependency setup

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.shantoislam.agenticwebview:browser-webview:0.3.0")
}
```

---

## Activity integration recipe

Hosts must be created or attached on the Android main thread, and their lifecycle must be coordinated with the hosting Activity or Fragment:

```kotlin
package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.shantoislam.agenticwebview.api.ActionConfiguration
import dev.shantoislam.agenticwebview.api.AgenticBrowserConfiguration
import dev.shantoislam.agenticwebview.api.BrowserEvent
import dev.shantoislam.agenticwebview.api.HostRule
import dev.shantoislam.agenticwebview.api.NavigationPolicy
import dev.shantoislam.agenticwebview.api.NavigationRequest
import dev.shantoislam.agenticwebview.api.PointerActionStrategy
import dev.shantoislam.agenticwebview.webview.AgenticBrowserHost
import dev.shantoislam.agenticwebview.webview.BrowserDialogDecision
import dev.shantoislam.agenticwebview.webview.BrowserDialogRequest
import dev.shantoislam.agenticwebview.webview.BrowserHostDelegate
import dev.shantoislam.agenticwebview.webview.BrowserPopupDecision
import dev.shantoislam.agenticwebview.webview.BrowserPopupRequest
import kotlinx.coroutines.launch

class BrowserActivity : ComponentActivity() {

    private lateinit var host: AgenticBrowserHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Configure browser policy and action behavior
        val configuration = AgenticBrowserConfiguration(
            actions = ActionConfiguration(
                pointerStrategy = PointerActionStrategy.NATIVE_PREFERRED,
            ),
            navigation = NavigationPolicy(
                allowedSchemes = setOf("https"),
                allowedHosts = setOf(HostRule.DomainAndSubdomains("example.com")),
            ),
        )

        // 2. Custom host delegate (override only what your application needs)
        val delegate = object : BrowserHostDelegate {
            override fun onDialog(request: BrowserDialogRequest): BrowserDialogDecision {
                // Confirm dialogs (or return BrowserDialogDecision.Cancel)
                return BrowserDialogDecision.Confirm(promptValue = request.defaultValue)
            }

            override fun onPopup(request: BrowserPopupRequest): BrowserPopupDecision {
                // Redirect popups into the current governed session
                return BrowserPopupDecision.OPEN_IN_CURRENT_SESSION
            }
        }

        // 3. Create host and attach to View hierarchy
        host = AgenticBrowserHost.create(
            context = this,
            configuration = configuration,
            delegate = delegate, // Or BrowserHostDelegate.DenyAll
        )
        setContentView(host.view)

        // 4. Collect lifecycle events reactively
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                host.session.events.collect { event ->
                    when (event) {
                        is BrowserEvent.StateChanged -> {
                            println("Session transitioned: ${event.previous.phase} -> ${event.current.phase}")
                        }
                        is BrowserEvent.DocumentRevisionChanged -> {
                            println("DOM mutated on document ${event.documentId.value}: new rev ${event.revision.value}")
                        }
                        is BrowserEvent.NavigationBlocked -> {
                            println("Policy blocked navigation to: ${event.url} (${event.reason})")
                        }
                        is BrowserEvent.RendererTerminated -> {
                            println("WebView renderer terminated (crashed: ${event.didCrash})")
                        }
                        else -> Unit
                    }
                }
            }
        }

        // 5. Trigger initial navigation
        lifecycleScope.launch {
            host.session.navigate(NavigationRequest("https://example.com"))
        }
    }

    override fun onResume() {
        super.onResume()
        host.onResume()
    }

    override fun onPause() {
        host.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        host.close()
        super.onDestroy()
    }
}
```

---

## BrowserHostDelegate Callbacks

`BrowserHostDelegate` handles host-authority decisions. All methods have safe default implementations:

| Callback | Default Behavior | Purpose |
| --- | --- | --- |
| `onDialog(request)` | `BrowserDialogDecision.Cancel` | Handle `alert`, `confirm`, `prompt`, or `beforeunload` dialogs |
| `onPopup(request)` | `BrowserPopupDecision.DENY` | Handle `window.open` or new window requests |
| `onPermission(request)` | `emptySet()` (deny all) | Grant or deny web permissions (camera, microphone, geolocation) |
| `onFileChooser(request, respond)` | `false` (cancel) | Handle `<input type="file">` file selection |
| `onDownload(request)` | No-op | Handle file download intents |
| `onNavigationBlocked(url, reason)` | No-op | Notification when policy blocks navigation |
| `onSslError(url, primaryError)` | No-op | Notification when SSL certificate validation fails |
| `onSafeBrowsingHit(url, threatType)` | No-op | Notification when Google Safe Browsing flags an unsafe URL |

Pass `BrowserHostDelegate.DenyAll` if your application requires no custom authority handling.

---

## Attaching an existing WebView

If your application already manages an existing `WebView` instance, use `AgenticBrowserHost.attach`:

```kotlin
val host = AgenticBrowserHost.attach(
    webView = existingWebView,
    configuration = AgenticBrowserConfiguration(),
    delegate = BrowserHostDelegate.DenyAll,
)
```

> [!WARNING]
> Never replace the installed `WebViewClient` or `WebChromeClient` after attaching. Doing so breaks the protocol bridge, navigation interception, and lifecycle event streams. Use `BrowserHostDelegate` and `session.events` instead.

---

## Related documentation

- [Jetpack Compose integration](compose.md)
- [Configuration options](configuration.md)
- [Command reference](commands.md)
- [Diagnostics & error handling](diagnostics.md)
- [Lifecycle & event stream](lifecycle.md)
