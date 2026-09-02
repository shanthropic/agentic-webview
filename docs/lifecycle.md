# Lifecycle and cancellation

The session moves through `ATTACHED`, `NAVIGATING`, `DOCUMENT_CREATED`, `RUNTIME_INITIALIZING`, `INTERACTIVE`, `STABILIZING`, and `READY`, or a terminal/error state. The reducer rejects invalid transitions.

Navigation is serialized. Starting navigation cancels all pending requests tied to the previous document. Caller cancellation stops loading and marks the attempted navigation failed. Renderer termination cancels document work, emits an event, and makes the session unusable; create a new host to recover.

`close()` is idempotent. It closes pending requests, removes the JavaScript interface, releases screenshot resources, detaches installed clients, and destroys only SDK-owned WebViews.
