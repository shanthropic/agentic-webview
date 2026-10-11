# Framework-neutral agent tools

The `agent-tools` module provides typed, framework-neutral tools formatted as JSON Schema for integration with any LLM function-calling framework (such as OpenAI, Anthropic, Gemini, LangChain, or custom agent runtimes).

## Dependency setup

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.shantoislam.agenticwebview:browser-api:VERSION")
    implementation("dev.shantoislam.agenticwebview:agent-tools:VERSION")
}
```

---

## StandardBrowserTools API

`StandardBrowserTools` implements `AgentToolDispatcher`. It binds to an `AgenticBrowserSession` and handles JSON tool invocations without giving the LLM or agent direct access to `WebView` internal references.

```kotlin
import dev.shantoislam.agenticwebview.tools.BrowserToolProfile
import dev.shantoislam.agenticwebview.tools.StandardBrowserTools
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// 1. Create tools instance with a profile
val browserTools = StandardBrowserTools(
    session = host.session,
    profile = BrowserToolProfile.STANDARD, // MINIMAL, STANDARD, or ADVANCED
)

// 2. Export tool definitions for LLM registration
browserTools.definitions.forEach { definition ->
    println("Tool: ${definition.name}")
    println("Description: ${definition.description}")
    println("Input Schema: ${definition.inputSchema}")
}

// 3. Programmatically invoke a tool from an LLM function call response
val arguments = buildJsonObject {
    put("url", "https://example.com")
}
val invocationResult = browserTools.invoke("browser_navigate", arguments)

if (invocationResult.success) {
    println("Summary: ${invocationResult.output["summary"]}")
} else {
    println("Error code: ${invocationResult.output["code"]}")
    println("Should re-observe: ${invocationResult.output["recommendObservation"]}")
}
```

---

## Tool Profiles

| Profile | Tool Count | Included Tools |
| --- | --- | --- |
| `BrowserToolProfile.MINIMAL` | 4 | `browser_observe`, `browser_navigate`, `browser_click`, `browser_type_text` |
| `BrowserToolProfile.STANDARD` | 10 | MINIMAL + `browser_select_option`, `browser_scroll`, `browser_press_keys`, `browser_go_back`, `browser_go_forward`, `browser_reload` |
| `BrowserToolProfile.ADVANCED` | 12 | STANDARD + `browser_long_press`, `browser_scroll_into_view` |

---

## Detailed Schemas for All 12 Tools

### Target Element Reference Structure
Tools that interact with webpage elements accept a `target` JSON object matching:

```json
{
  "type": "object",
  "properties": {
    "documentId": { "type": "string", "minLength": 1 },
    "frameId": { "type": "string", "minLength": 1 },
    "elementId": { "type": "string", "minLength": 1 },
    "observedAtRevision": { "type": "integer", "minimum": 0 }
  },
  "required": ["documentId", "frameId", "elementId", "observedAtRevision"],
  "additionalProperties": false
}
```

---

### 1. `browser_observe`
Inspects the current webpage and returns its semantic DOM tree with stable element references.
- **Parameters**:
  - `include_screenshot` (boolean, optional, default `false`): When true, includes base64-encoded screenshot.
  - `include_compact_text` (boolean, optional, default `true`): When true, includes compact readable page text.
  - `include_offscreen_content` (boolean, optional, default `false`): Include nodes outside current viewport.

### 2. `browser_navigate`
Navigates to an HTTP or HTTPS URL permitted by host security policy.
- **Parameters**:
  - `url` (string, required): Destination URL (must not be blank).

### 3. `browser_click`
Clicks an actionable element reference returned by the latest observation.
- **Parameters**:
  - `target` (ElementRef object, required).

### 4. `browser_type_text`
Enters text into an editable input or textarea element.
- **Parameters**:
  - `target` (ElementRef object, required).
  - `text` (string, required): Text to insert or set.
  - `mode` (string enum, optional, default `"replace_all"`): One of `"replace_all"`, `"append"`, `"insert_at_selection"`, or `"clear"`.

### 5. `browser_select_option`
Selects an option within a `<select>` dropdown element.
- **Parameters**:
  - `target` (ElementRef object, required).
  - **Exactly one of**:
    - `value` (string): The option's `value` attribute.
    - `label` (string): The option's visible display text.
    - `index` (integer, minimum 0): The zero-based index of the option.

### 6. `browser_scroll`
Scrolls the document viewport or a specific container element.
- **Parameters**:
  - `target` (ElementRef object, optional): Container element to scroll; defaults to main document viewport.
  - `delta_x_css_px` (number, optional, default `0.0`): Horizontal scroll delta in CSS pixels.
  - `delta_y_css_px` (number, optional, default `0.0`): Vertical scroll delta in CSS pixels.
  *(Note: At least one scroll delta must be non-zero)*.

### 7. `browser_press_keys`
Dispatches a keyboard chord to the currently focused page element.
- **Parameters**:
  - `key` (string, required): KeyboardEvent key value (e.g. `"Enter"`, `"Tab"`, `"Escape"`, `"ArrowDown"`, `"a"`).
  - `control` (boolean, optional, default `false`).
  - `alt` (boolean, optional, default `false`).
  - `shift` (boolean, optional, default `false`).
  - `meta` (boolean, optional, default `false`).

### 8. `browser_go_back`
Navigates to the previous entry in browser session history.
- **Parameters**: None (`{}`).

### 9. `browser_go_forward`
Navigates to the next entry in browser session history.
- **Parameters**: None (`{}`).

### 10. `browser_reload`
Reloads the current webpage.
- **Parameters**: None (`{}`).

### 11. `browser_long_press`
Long-presses an actionable element.
- **Parameters**:
  - `target` (ElementRef object, required).
  - `duration_ms` (integer, optional, minimum `1`, maximum `60000`, default `500`): Touch hold duration in milliseconds.

### 12. `browser_scroll_into_view`
Brings a referenced element into the visible viewport.
- **Parameters**:
  - `target` (ElementRef object, required).
  - `alignment` (string enum, optional, default `"center"`): One of `"start"`, `"center"`, `"end"`, or `"nearest"`.

---

## Tool Invocation Output Envelope

Every tool invocation returns a deterministic, machine-readable JSON structure.

### Success Envelope
```json
{
  "status": "success",
  "summary": "Browser action completed",
  "value": {
    "commandType": "click",
    "strategy": "DOM_POINTER",
    "documentId": "doc_1",
    "revisionBefore": 1,
    "revisionAfter": 2,
    "dispatched": true,
    "verified": true,
    "pageChanged": true
  },
  "recommendObservation": true,
  "context": {
    "documentId": "doc_1",
    "revision": 2
  },
  "diagnostics": {
    "operationId": "op_987",
    "durationMs": 35,
    "strategy": "DOM_POINTER",
    "attempts": 1,
    "warnings": []
  }
}
```

### Error Envelope
When a browser action fails or encounters an error, the envelope provides a stable `code` and indicates whether the agent should re-observe:

```json
{
  "status": "error",
  "code": "STALE_ELEMENT",
  "summary": "Element reference is stale",
  "error": {
    "type": "stale_element",
    "target": {
      "documentId": "doc_1",
      "frameId": "frame_0",
      "elementId": "btn_submit",
      "observedAtRevision": 1
    },
    "message": "Element reference is stale"
  },
  "recommendObservation": true,
  "context": {
    "documentId": "doc_1",
    "revision": 2
  },
  "diagnostics": {
    "durationMs": 12,
    "attempts": 1,
    "warnings": []
  }
}
```

Common error codes in the output envelope include:
- `STALE_ELEMENT`: Target element was detached or DOM mutated; call `browser_observe` and re-query.
- `ELEMENT_OCCLUDED`: Target element is covered by another element; call `browser_scroll_into_view`.
- `ELEMENT_NOT_ACTIONABLE`: Target is disabled, invisible, or read-only.
- `ELEMENT_NOT_FOUND`: Element reference does not exist in the active document.
- `PAGE_NOT_READY`: Browser is currently navigating or stabilizing.
- `NAVIGATION_BLOCKED`: Navigation was blocked by security host policy.
- `INVALID_ARGUMENTS`: Tool arguments failed JSON Schema validation.

---

## Related documentation

- [Official Koog agent integration](koog.md)
- [Transport-neutral JSON-RPC 2.0 server](jsonrpc.md)
- [Command reference](commands.md)
- [Observation model](observations.md)
