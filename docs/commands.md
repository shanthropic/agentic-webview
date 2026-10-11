# Command & action reference

All browser interactions in Agentic WebView are dispatched through `AgenticBrowserSession.execute(BrowserCommand)`, `session.await(WaitCondition)`, or `session.navigateHistory(HistoryNavigationRequest)`.

Commands are strictly typed, document-bound, and verified against observable postconditions before returning a `CommandReceipt`.

---

## Command overview

| Command | Target | Parameters | Strategy | Verification postcondition |
| --- | --- | --- | --- | --- |
| `BrowserCommand.Click` | `ElementRef` | `button: PointerButton = PRIMARY` | `DOM_POINTER` or `ANDROID_NATIVE_POINTER` | Event dispatched, focus changed, value modified, revision incremented, element detached, or navigation started |
| `BrowserCommand.LongPress` | `ElementRef` | `durationMs: Long = 500` (1..60,000) | `DOM_POINTER` | Pointer/touch sequence held for duration and observable postcondition verified |
| `BrowserCommand.TypeText` | `ElementRef` | `text: String`, `mode: TextInputMode` | `DOM_NATIVE_SETTER` | Native prototype setter invoked, input/change events fired, target element value equals expected text |
| `BrowserCommand.SelectOption` | `ElementRef` | `option: SelectOptionMatcher` | `DOM_SELECT` | Target `<select>` option matched by value, label, or index; change events dispatched; selection updated |
| `BrowserCommand.PressKeys` | Focused element | `chord: KeyChord` | `DOM_KEYBOARD` | Key down/up sequence dispatches to active element with modifiers; effect observed |
| `BrowserCommand.Scroll` | Page / Element | `delta: ScrollDelta` | `DOM_SCROLL` | Scroll coordinates of viewport or element container change by delta CSS pixels |
| `BrowserCommand.ScrollIntoView` | `ElementRef` | `alignment: ScrollAlignment = CENTER` | `DOM_SCROLL` | Target element's bounding rect intersects the visible viewport |

---

## Action execution strategies & native pointer simulation

Pointer interactions support two execution strategies, configured via `ActionConfiguration.pointerStrategy`:

1. **`PointerActionStrategy.DOM_ONLY` (Default)**: Dispatches synthetic PointerEvent, MouseEvent, and TouchEvent sequences directly in the DOM JavaScript context (`CommandReceipt.strategy = DOM_POINTER`). Fast and headless-friendly.
2. **`PointerActionStrategy.NATIVE_PREFERRED`**: Synthesizes real Android `MotionEvent` touch gestures (`CommandReceipt.strategy = ANDROID_NATIVE_POINTER`) for primary clicks directly into the Android `WebView` window.
   - The SDK computes the target element's exact viewport coordinates (accounting for scroll offset, device pixel ratio, and frame nesting).
   - Synthesizes hardware-level ACTION_DOWN followed by ACTION_UP gestures.
   - **Why this matters**: Modern web applications, gesture libraries, and anti-bot protections frequently ignore synthetic DOM events (`event.isTrusted === false`). Native pointer simulation produces genuine hardware touch events with `event.isTrusted === true`.

```kotlin
// Enabling native touch pointer simulation:
val configuration = AgenticBrowserConfiguration(
    actions = ActionConfiguration(
        pointerStrategy = PointerActionStrategy.NATIVE_PREFERRED,
        geometryStableCycles = 2,
        geometryTolerancePx = 1.0,
    ),
)
```

---

## Practical command recipes

### 1. Click an element
```kotlin
val clickReceipt = host.session.execute(
    BrowserCommand.Click(
        target = targetElementRef,
        button = PointerButton.PRIMARY, // PRIMARY (left), SECONDARY (right), MIDDLE
    )
)
```

### 2. Long press an element
```kotlin
val longPressReceipt = host.session.execute(
    BrowserCommand.LongPress(
        target = targetElementRef,
        durationMs = 800, // Duration in milliseconds (1..60,000)
    )
)
```

### 3. Type text into input or textarea
Text inputs support four update modes (`TextInputMode`):
- `REPLACE_ALL`: Clears existing value and inserts the specified text (default).
- `APPEND`: Keeps existing value and appends new text at the end.
- `INSERT_AT_SELECTION`: Replaces currently selected text or inserts at caret.
- `CLEAR`: Clears the input field (ignores `text`).

```kotlin
val typeReceipt = host.session.execute(
    BrowserCommand.TypeText(
        target = inputElementRef,
        text = "user@example.com",
        mode = TextInputMode.REPLACE_ALL,
    )
)
```

### 4. Select dropdown option
Select an option in a `<select>` element by value, visible text label, or zero-based index:

```kotlin
// Select by option attribute value
host.session.execute(
    BrowserCommand.SelectOption(
        target = selectElementRef,
        option = SelectOptionMatcher.Value("ca_en"),
    )
)

// Select by visible label
host.session.execute(
    BrowserCommand.SelectOption(
        target = selectElementRef,
        option = SelectOptionMatcher.Label("Canada (English)"),
    )
)

// Select by index
host.session.execute(
    BrowserCommand.SelectOption(
        target = selectElementRef,
        option = SelectOptionMatcher.Index(2),
    )
)
```

### 5. Send keyboard shortcuts / chords
Dispatches keyboard events to the currently focused page element:

```kotlin
// Submit form via Enter key
host.session.execute(
    BrowserCommand.PressKeys(
        chord = KeyChord(key = "Enter")
    )
)

// Dispatch Ctrl+A (Select All)
host.session.execute(
    BrowserCommand.PressKeys(
        chord = KeyChord(key = "a", control = true)
    )
)
```

### 6. Scroll viewport or scroll container
Scroll the full page or a specific container element by CSS pixels:

```kotlin
// Scroll main page down by 500 CSS pixels
host.session.execute(
    BrowserCommand.Scroll(
        target = ScrollTarget.Page,
        delta = ScrollDelta(xCssPx = 0.0, yCssPx = 500.0),
    )
)

// Scroll a specific scrollable element horizontally
host.session.execute(
    BrowserCommand.Scroll(
        target = ScrollTarget.Element(scrollableDivRef),
        delta = ScrollDelta(xCssPx = 200.0, yCssPx = 0.0),
    )
)
```

### 7. Scroll element into view
Align an element within the visible viewport:

```kotlin
host.session.execute(
    BrowserCommand.ScrollIntoView(
        target = targetElementRef,
        alignment = ScrollAlignment.CENTER, // START, CENTER, END, NEAREST
    )
)
```

---

## Dynamic awaiting (`session.await`)

`AgenticBrowserSession.await(condition)` suspends until a specified condition is satisfied or the runtime request timeout expires.

### Available wait conditions:
1. `WaitCondition.PageReady`: Awaits standard page readiness (DOM settled and quiet window satisfied).
2. `WaitCondition.DomQuiet(quietWindowMs)`: Awaits a mutation-free period (useful for single-page applications rendering client-side content).
3. `WaitCondition.ElementPresent(target)`: Awaits until an element reference exists and is attached in the DOM.

```kotlin
// Await a 500ms DOM quiet window after triggering dynamic content
when (val waitResult = host.session.await(WaitCondition.DomQuiet(quietWindowMs = 500))) {
    is BrowserResult.Success -> {
        val receipt = waitResult.value
        println("DOM quiet satisfied at epoch ms: ${receipt.satisfiedAtEpochMs}")
        println("Document revision: ${receipt.revision.value}")
    }
    is BrowserResult.Failure -> {
        println("Wait failed: ${waitResult.error.message}")
    }
}
```

---

## History navigation (`session.navigateHistory`)

Navigate backward, forward, or reload the current webpage:

```kotlin
// Navigate Back
val backReceipt = host.session.navigateHistory(
    HistoryNavigationRequest(
        operation = NavigationOperation.BACK,
        readiness = WaitCondition.PageReady,
    )
)

// Reload current page
val reloadReceipt = host.session.navigateHistory(
    HistoryNavigationRequest(
        operation = NavigationOperation.RELOAD,
        readiness = WaitCondition.PageReady,
    )
)
```

If history is unavailable (e.g., `canGoBack()` is false), it returns `BrowserResult.Failure(BrowserError.NavigationHistoryUnavailable(operation))` instead of failing silently.

---

## Verification semantics and CommandReceipt

A command returns a `BrowserResult.Success(CommandReceipt)` only when the browser observed verified postconditions:

```kotlin
data class CommandReceipt(
    val commandType: String,                 // e.g. "click", "type_text"
    val strategy: ActionExecutionStrategy,   // DOM_POINTER, ANDROID_NATIVE_POINTER, etc.
    val documentId: DocumentId,              // Document on which action executed
    val revisionBefore: ObservationRevision, // DOM revision prior to action
    val revisionAfter: ObservationRevision,  // DOM revision after action
    val dispatched: Boolean,                 // Whether event was dispatched
    val verified: Boolean,                   // True if postcondition observed
    val pageChanged: Boolean,                // True if revision changed or navigation started
)
```

If an action was successfully dispatched but produced no detectable effect (for example, clicking a disabled `div` or non-interactive text that triggered no listeners, mutations, or navigation), the session returns `BrowserResult.Failure(BrowserError.ActionNotVerified)` containing the receipt, rather than falsely reporting success.

---

## Related documentation

- [Observation model & ElementRef](observations.md)
- [Configuration options](configuration.md)
- [Diagnostics & Error catalog](diagnostics.md)
- [Framework-neutral agent tools](agent-tools.md)
