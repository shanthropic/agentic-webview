# Framework-neutral agent tools

```kotlin
val tools: AgentToolDispatcher = StandardBrowserTools(
    session = host.session,
    profile = BrowserToolProfile.STANDARD,
)
```

The standard profile publishes JSON Schema for:

1. `browser_observe`
2. `browser_navigate`
3. `browser_click`
4. `browser_type_text`
5. `browser_select_option`
6. `browser_scroll`
7. `browser_press_keys`
8. `browser_go_back`
9. `browser_go_forward`
10. `browser_reload`

`MINIMAL` exposes observe, navigate, click, and type. `ADVANCED` additionally exposes long press and scroll-into-view.

Every result contains `status`, a stable error `code` when applicable, `summary`, document/revision `context`, `recommendObservation`, and diagnostics. Adapters must return this structure intact rather than convert failures to prose.

Typed SDK observations keep screenshots as binary `ByteArray` data. When `browser_observe` explicitly requests a screenshot, the JSON tool boundary emits `screenshot.dataBase64`, `mimeType`, `widthPx`, and `heightPx`; it never expands bytes into a JSON integer array.

Page observations begin with an explicit untrusted-webpage marker. The agent's authority, allowed hosts, confirmation rules, and secret-handling policy belong in trusted application instructions, never in page content.
