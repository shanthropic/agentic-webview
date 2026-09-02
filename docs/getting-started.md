# Getting started

Agentic WebView requires Android API 28+, Java 11 bytecode, and a host that can display an Android `WebView`.

Navigation is HTTPS-only by default. Local fixtures or intentionally supported cleartext sites must opt in with `NavigationPolicy(allowedSchemes = setOf("https", "http"))`; the host application must also permit cleartext traffic where Android requires it.

## Choose modules

```kotlin
dependencies {
    implementation("dev.shantoislam.agenticwebview:browser-webview:VERSION")
    implementation("dev.shantoislam.agenticwebview:browser-compose:VERSION") // Compose only
    implementation("dev.shantoislam.agenticwebview:agent-tools:VERSION") // agent tools only
}
```

The Android library requires the application to declare Internet access:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Create a session

For an SDK-owned view:

```kotlin
val host = AgenticBrowserHost.create(
    context = activity,
    configuration = AgenticBrowserConfiguration(),
)
container.addView(host.view)
```

For an existing WebView:

```kotlin
val host = AgenticBrowserHost.attach(existingWebView, configuration)
```

Creation and attachment happen on the main thread. Close the host when its UI owner is destroyed. The host owns a newly created WebView; it does not destroy an attached WebView.

## Navigate and observe

```kotlin
val navigation = host.session.navigate(NavigationRequest("https://example.com"))
if (navigation is BrowserResult.Success) {
    when (val observation = host.session.observe()) {
        is BrowserResult.Success -> println(observation.value.compactText)
        is BrowserResult.Failure -> println(observation.error)
    }
}
```

Do not cache element references across navigation. Use references returned by the latest observation and handle `StaleElementReference` by observing again.

## Execute a typed command

```kotlin
val target = observation.nodes.firstNotNullOf { it.elementRef }
val result = host.session.execute(BrowserCommand.Click(target))
```

All operations return `BrowserResult`; expected browser failures are values, not exceptions. Coroutine cancellation remains cancellation and aborts associated work.

Continue with [Views](views.md), [Compose](compose.md), or [agent tools](agent-tools.md).
