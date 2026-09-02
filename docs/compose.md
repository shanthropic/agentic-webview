# Compose integration

```kotlin
@Composable
fun BrowserScreen() {
    val host = rememberAgenticBrowserHost(
        configuration = AgenticBrowserConfiguration(),
        delegate = remember { AppBrowserDelegate() },
    )

    AgenticBrowserView(
        host = host,
        modifier = Modifier.fillMaxSize(),
    )
}
```

`rememberAgenticBrowserHost` creates and closes the host. A configuration or delegate identity change intentionally creates a new session so policy cannot silently change underneath a live document. `AgenticBrowserView` forwards lifecycle pause/resume and contains no Material UI or application state.
