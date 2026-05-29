# Agent Integration Guide

This guide explains how to use the `AgenticWebController` to build LLM-powered agents that can perceive and interact with web pages.

## 1. Capturing Page State

The `captureState()` method provides everything an agent needs to "see" the page.

```kotlin
scope.launch {
    when (val result = controller.captureState()) {
        is AgentResult.Success -> {
            val state = result.data
            // 1. Pass the compact tree to your LLM text prompt
            val promptTree = state.compactTree 
            
            // 2. Pass the screenshot to your Vision model (if enabled)
            val screenshot = state.screenshotBase64
            
            println("Agent is looking at: ${state.title} (${state.url})")
        }
        is AgentResult.Error -> {
            println("Failed to capture state: ${result.error}")
        }
    }
}
```

### The Compact Tree
The `compactTree` is a token-optimized representation of the DOM. Each element has a `[highlightIndex]` that the agent uses to perform actions.

Example output:
```text
[15]<button title="Submit" />
[16]<input type="text" placeholder="Search..." />
```

## 2. Executing Actions

Once your agent decides on an action, execute it using `executeAction()`. The SDK handles retries and stability checks automatically.

### Full Action Reference

#### Navigation
- `AgentAction.Navigate(url: String)`: Loads a new URL.
- `AgentAction.GoBack`, `AgentAction.GoForward`, `AgentAction.Refresh`: Standard browser controls.
- `AgentAction.Wait(durationMs: Long)`: Pauses the agent.

#### Interaction
- `AgentAction.Click(agentId: String)`: Native tap at the element's center.
- `AgentAction.LongPress(agentId: String, durationMs: Long)`: Long press (default 500ms).
- `AgentAction.InputText(agentId: String, text: String, clearFirst: Boolean)`: Focuses and types.
- `AgentAction.SelectOption(agentId: String, value: String)`: Sets a `<select>` element's value directly.
- `AgentAction.SendKeys(keys: String)`: Simulates keyboard shortcuts (e.g., `"Control+A"`).

#### Advanced Scrolling
- `AgentAction.Scroll(direction: ScrollDirection, amount: Float)`: Scroll by fraction of viewport (0.5 = 50%).
- `AgentAction.ScrollToPercent(yPercent: Float, agentId: String?)`: Scroll to specific % of page or container.
- `AgentAction.ScrollToText(text: String, nth: Int)`: Find text and scroll it into view.
- `AgentAction.ScrollToTop(agentId?)`, `AgentAction.ScrollToBottom(agentId?)`: Jump to extremes.
- `AgentAction.PreviousPage(agentId: String?)`, `AgentAction.NextPage(agentId: String?)`: Scroll by exactly one viewport height.

#### Dropdowns & Completion
- `AgentAction.GetDropdownOptions(agentId: String)`: Enumerate all `<option>` elements of a `<select>`.
- `AgentAction.SelectDropdownOption(agentId, text)`: Select `<option>` by visible text.
- `AgentAction.Done(text: String, success: Boolean)`: Signal task completion.

## 3. Monitoring Progress

You can observe `loadingProgress` (0-100) to show a progress bar in your UI.

```kotlin
val progress by controller.loadingProgress.collectAsState()
if (progress < 100) {
    LinearProgressIndicator(progress = progress / 100f)
}
```

## 4. Dropdown Handling
For `<select>` elements, you can retrieve all available options.

```kotlin
val optionsResult = controller.getDropdownOptions(agentId = "22")
if (optionsResult is AgentResult.Success) {
    val options = optionsResult.data // List<DropdownOption>
}

// Select an option by its visible text
controller.executeAction(AgentAction.SelectDropdownOption(agentId = "22", text = "Option A"))
```
