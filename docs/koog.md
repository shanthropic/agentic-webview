# Koog adapter

Add `integration-koog`, construct the generic dispatcher, and wrap it:

```kotlin
val dispatcher = StandardBrowserTools(host.session)
val koogTools = KoogBrowserTools(dispatcher)
```

`KoogBrowserTools` is a reflective Koog `ToolSet`. Element tools expose `documentId`, `frameId`, `elementId`, and `observedAtRevision` as typed arguments copied from `browser_observe`; agents never need to double-encode a target JSON string. The adapter only translates Koog method arguments and serialized results; it has no WebView dependency and does not implement browser behavior.

For another framework, adapt `AgentToolDispatcher.definitions` and `invoke`. The JSON-RPC module is a second working example and supports `tools/list`, `tools/call`, and response-free notifications over any host-selected transport.
