# Integration Guide

This guide covers how to add the Agentic WebView SDK to your Android project and initialize it.

## 1. Add Dependency

Add the following to your `build.gradle.kts` (or `build.gradle`):

```kotlin
dependencies {
    // Replace with the latest version
    implementation("com.shantoislamdev:agentic-webview:0.1.0")
}
```

## 2. Basic Setup (Views)

To use the SDK in a standard View-based layout:

### Layout XML
```xml
<com.shantoislamdev.agenticwebview.AgenticWebView
    android:id="@+id/agentic_webview"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### Activity/Fragment
```kotlin
val webView = findViewById<AgenticWebView>(R.id.agentic_webview)
val controller = AgenticWebController()

// Attach the controller to the WebView
controller.attach(webView)

// Load a URL
webView.loadUrl("https://www.example.com")
```

## 3. Jetpack Compose Integration

If you are using Jetpack Compose, use the provided `AgenticWebViewComposable`.

```kotlin
val controller = remember { AgenticWebController() }

AgenticWebViewComposable(
    controller = controller,
    modifier = Modifier.fillMaxSize(),
    config = AgenticWebViewConfig.Builder()
        .setEnableDebugLogging(true)
        .build()
)
```

## 4. Configuration Options

You can customize the SDK's behavior using `AgenticWebViewConfig`.

| Option | Default | Description |
| :--- | :--- | :--- |
| `screenshotEnabled` | `true` | Capture screenshots during state capture. |
| `maxDomElements` | `500` | Limit the number of nodes in the accessibility tree. |
| `enableAntiDetection` | `true` | Hide WebDriver flags to prevent bot detection. |
| `viewportExpansion` | `0` | Px to capture outside the viewport (-1 for full page). |
| `jsEvaluationTimeoutMs`| `5000`| Timeout for JavaScript execution. |
| `pageSettleTimeoutMs` | `10000`| Max wait time for page to reach `COMPLETE` state. |
| `elementStabilityTimeoutMs`| `1000`| Wait time for element positions to stabilize before input. |

## 5. Security & Domain Control

For production apps, you should restrict where the agent can navigate using domain allow-lists or deny-lists.

```kotlin
val config = AgenticWebViewConfig.Builder()
    .setAllowedHosts(setOf("google.com", "github.com"))
    .setDeniedHosts(setOf("malicious-site.com"))
    .setHomeUrl("https://my-safe-homepage.com")
    .build()
```

- **allowedHosts**: If set, the WebView will block any navigation to hosts not in this set.
- **deniedHosts**: If set, navigation to these hosts will be blocked.
- **homeUrl**: If a navigation is blocked, the WebView can optionally redirect the user here.
