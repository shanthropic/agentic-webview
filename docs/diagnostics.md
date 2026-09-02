# Diagnostics and troubleshooting

Enable diagnostics and provide a sink:

```kotlin
val configuration = AgenticBrowserConfiguration(
    diagnostics = DiagnosticsConfiguration(enabled = true),
)
val sink = BrowserDiagnosticsSink { event -> telemetry.record(event) }
val host = AgenticBrowserHost.create(context, configuration, diagnosticsSink = sink)
```

Diagnostic events use categories, operation/document/revision context, durations when available, and bounded attributes. Page content and action text remain disabled by default. Avoid enabling content-bearing diagnostics outside controlled development.

Common failures are returned as `BrowserError`: policy blocks, unavailable history, page-not-ready, stale or occluded elements, protocol mismatch, resource limits, timeout, cancellation, renderer termination, and screenshot failures. Handle them by code; human messages are not a stable API.
