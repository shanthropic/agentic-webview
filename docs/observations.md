# Observation model

`BrowserObservation` describes a webpage at a specific document revision. It provides an autonomous agent with a structured, semantic view of the page, stable element references for action dispatch, and optional visual screenshots.

---

## Observation API Recipe

```kotlin
import dev.shantoislam.agenticwebview.api.BrowserResult
import dev.shantoislam.agenticwebview.api.ObservationOptions
import dev.shantoislam.agenticwebview.api.PageNodeKind
import dev.shantoislam.agenticwebview.api.PageNodeState

lifecycleScope.launch {
    // 1. Configure observation options
    val options = ObservationOptions(
        includeScreenshot = true,       // Capture hardware/PixelCopy screenshot
        includeCompactText = true,      // Generate human-readable text summary
        includeOffscreenContent = false, // Limit to elements within or near viewport
    )

    // 2. Capture observation
    when (val result = host.session.observe(options)) {
        is BrowserResult.Success -> {
            val observation = result.value

            println("Document: ${observation.document.title} (${observation.document.url})")
            println("Revision: ${observation.revision.value}")
            println("Content Trust: ${observation.contentTrust}") // UNTRUSTED_WEBPAGE

            // Viewport metrics
            val vp = observation.viewport
            println("Viewport: ${vp.widthCssPx}x${vp.heightCssPx} (DPR: ${vp.devicePixelRatio})")

            // 3. Inspect semantic nodes
            for (node in observation.nodes) {
                node.elementRef?.let { ref ->
                    println("Actionable [${node.role ?: node.kind}]: ${node.accessibleName ?: node.text}")
                    println("  Ref: Doc=${ref.documentId.value}, Frame=${ref.frameId.value}, ID=${ref.elementId.value}, Rev=${ref.observedAtRevision.value}")
                    if (PageNodeState.DISABLED in node.states) println("  State: Disabled")
                    if (PageNodeState.EDITABLE in node.states) println("  State: Editable")
                }
            }

            // 4. Access compact text
            println("Compact text preview:\n${observation.compactText}")

            // 5. Access screenshot (if requested)
            observation.screenshot?.let { screenshot ->
                println("Screenshot captured: ${screenshot.widthPx}x${screenshot.heightPx} (${screenshot.mimeType}, ${screenshot.bytes.size} bytes)")
            }

            // 6. Check for observation truncation
            observation.truncation?.let { truncation ->
                println("Warning: Observation was truncated due to ${truncation.reason} (limit: ${truncation.limit}, observed: ${truncation.observed})")
            }
        }
        is BrowserResult.Failure -> {
            println("Observation failed: ${result.error.message}")
        }
    }
}
```

---

## The Observation Data Structure

A `BrowserObservation` comprises:

- **`contentTrust`**: Always `UNTRUSTED_WEBPAGE`. All webpage text, labels, and attributes are treated as untrusted input to defend against prompt injection.
- **`document`**: `PageDocument(id, url, title, phase)`.
- **`revision`**: `ObservationRevision(Long)`. Monotonically increments whenever the DOM mutates.
- **`viewport`**: Scroll offsets, CSS pixel dimensions, visual scale, and device pixel ratio.
- **`frames`**: Hierarchy of frames (`PageFrame`) with origin information and `FrameCapability` (e.g. `OBSERVABLE_AND_ACTIONABLE`, `INACCESSIBLE_CROSS_ORIGIN`).
- **`nodes`**: Bounded list of semantic `PageNode` objects.
- **`compactText`**: Formatted plain text extraction suitable for feeding to an LLM context window.
- **`screenshot`**: Optional `BrowserScreenshot(bytes: ByteArray, mimeType: String, widthPx: Int, heightPx: Int)`.
- **`truncation`**: Optional `ObservationTruncation` detailing why an observation reached its configured limits.
- **`warnings`**: List of non-fatal warnings (e.g. depth limit reached, cross-origin frame encountered).
- **`metrics`**: Diagnostics metrics (traversal duration, visited node count, emitted node count, byte count).

---

## ElementRef and Identity Guarantees

Actionable nodes carry an `ElementRef`:

```kotlin
data class ElementRef(
    val documentId: DocumentId,
    val frameId: FrameId,
    val elementId: ElementId,
    val observedAtRevision: ObservationRevision,
)
```

- **Identity**: `elementId` is an internal registered DOM object handle, not a fragile CSS selector or XPath string.
- **Scoping**: An `ElementRef` is valid only for its specific document revision and frame.
- **Staleness**: If the element is removed from the DOM, replaced, or the page navigates, dispatching a command with that reference fails immediately with `BrowserError.StaleElementReference`.

---

## Privacy and Sensitive Content Redaction

To prevent exfiltrating sensitive user information:
1. **Password fields**: `<input type="password">` elements have their values and text redacted.
2. **Autocomplete hints**: Inputs with sensitive autocomplete attributes (e.g., `cc-number`, `cc-csc`, `current-password`) are masked.
3. **`data-agentic-sensitive`**: Any element bearing the `data-agentic-sensitive` HTML attribute has its text, attributes, and entire descendant subtree redacted.
4. **Screenshot masking**: Elements matching selectors defined in `ScreenshotConfiguration.maskCssSelectors` are painted over with black before capturing screenshots.
5. **URL Sanitization**: Observed links strip credentials, authentication tokens, query parameters, and fragments.

---

## Related documentation

- [Command reference](commands.md)
- [Configuration options](configuration.md)
- [Frames & Shadow DOM](frames-shadow-dom.md)
- [Privacy & prompt injection](privacy-prompt-injection.md)
- [Getting started](getting-started.md)
