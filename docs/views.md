# Android Views integration

Create a host or attach an existing WebView on the main thread:

```kotlin
class BrowserActivity : ComponentActivity() {
    private lateinit var host: AgenticBrowserHost

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        host = AgenticBrowserHost.create(this, browserConfiguration())
        setContentView(host.view)
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

Pass a `BrowserHostDelegate` to resolve host-authority requests such as dialogs, file selection, permissions, popups, and downloads. Defaults are restrictive. Never replace the installed WebView clients after attachment; use the delegate and `session.events` instead.
