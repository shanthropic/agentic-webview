# Developer Guide: Building AI Agents with Agentic WebView

This document provides guidance for developers building Large Language Model (LLM) based agents that utilize the Agentic WebView SDK. It explains how to interpret the SDK's output and how to effectively command the agent to browse the web.

## Perception: The "Eyes" of the Agent

Traditional web agents often struggle with raw HTML due to token limits, noise (scripts, styles), and complex layouts. This SDK provides a refined "Perception Layer" designed specifically for LLMs.

### The Accessibility Tree
The SDK parses the DOM into a simplified **Accessibility Tree**. Instead of thousands of lines of HTML, your agent receives a structured JSON array of only the interactive elements.

**Why this is better for LLMs:**
- **Token Efficiency**: Reduces the input size by 90-95% compared to raw HTML.
- **Stable Identifiers**: Each element is assigned a stable `agentId`. Unlike standard DOM indices, these IDs persist even when the page content changes or shifts due to mutations (like new content loading or ads appearing). This allows the LLM to refer to an element with confidence throughout its "thinking" loop.
- **Semantic Context**: Elements are enriched with roles (`button`, `link`, `input`), labels, and descriptions.
- **Occlusion Awareness**: The `occluded: true` flag tells the agent if an element is technically in the DOM but hidden behind something else (like a cookie banner or a modal), preventing the agent from trying to click unreachable targets.

### Multimodal Vision
For vision-capable models (like GPT-4o or Claude 3.5 Sonnet), the SDK provides a high-quality base64-encoded JPEG screenshot of the current viewport. 
- **Hardware Acceleration**: The SDK uses `PixelCopy` to ensure that hardware-accelerated content (videos, animations, canvas-based widgets) is correctly captured in the screenshot.
- **Coordinate Sync**: The coordinates in the accessibility tree are perfectly synced with the screenshot, allowing the model to "see" exactly what it is "touching."

### Selector Map
The SDK provides a `selectorMap` — a mapping from `highlightIndex` to `agentId`. This is useful when the LLM references elements by their visual index (e.g., "click element [5]") rather than by the full `agentId` string. The `compactTree` output uses these same indices.

### Compact Tree
For maximum token efficiency, the SDK produces a `compactTree` — a text-based representation of interactive elements formatted as:

```
[highlightIndex]<tag attr1=val1 attr2=val2>text />
```

This format is significantly more compact than the JSON accessibility tree and includes scroll position context. Use it when sending the page state to an LLM where token cost is a concern.

---

## Action: The "Hands" of the Agent

The SDK provides a set of deterministic tools. Your agent should be configured with a tool-calling interface that maps directly to the `AgentAction` sealed class.

### Reactive Feedback Loop
The SDK exposes a **Live State Stream** (`controller.state`). Instead of polling `captureState` repeatedly, your agent's host application can wait for the SDK to signal that the page has "settled" (network idle + DOM stable) before triggering the next move.

### Recommended Tool Schema
Map your LLM's functions to these SDK actions:

**Navigation:**

| Tool Name | Parameters | Description |
| :--- | :--- | :--- |
| `navigate` | `url` | Direct jump to a new page. |
| `go_back` | — | Navigate back in history. |
| `go_forward` | — | Navigate forward in history. |
| `refresh` | — | Reload the current page. |
| `wait` | `durationMs` | Useful when the agent expects an animation or a slow network update. |

**Interaction:**

| Tool Name | Parameters | Description |
| :--- | :--- | :--- |
| `click` | `agentId` | Taps on the element. |
| `long_press` | `agentId`, `durationMs` | Long press on the element. |
| `input_text` | `agentId`, `text`, `clearFirst` | Fuses focusing, clearing, and typing into one reliable operation. |
| `select_option` | `agentId`, `value` | Select a `<select>` option by value. |
| `send_keys` | `keys` | Keyboard shortcuts (e.g., `"Control+A"`, `"Enter"`). |

**Scrolling:**

| Tool Name | Parameters | Description |
| :--- | :--- | :--- |
| `scroll` | `direction`, `amount` | Direction: `UP`, `DOWN`, `LEFT`, `RIGHT`. Amount: `0.0` to `1.0`. |
| `scroll_to_percent` | `yPercent`, `agentId?` | Scroll to a percentage of the page height. |
| `scroll_to_text` | `text`, `nth` | Find visible text on the page and scroll to it. |
| `scroll_to_top` | `agentId?` | Scroll to the top of the page or a scrollable element. |
| `scroll_to_bottom` | `agentId?` | Scroll to the bottom of the page or a scrollable element. |
| `previous_page` | `agentId?` | Scroll up by one viewport height. |
| `next_page` | `agentId?` | Scroll down by one viewport height. |

**Dropdowns:**

| Tool Name | Parameters | Description |
| :--- | :--- | :--- |
| `get_dropdown_options` | `agentId` | Enumerate all `<select>` options. Result stored in `lastDropdownOptions`. |
| `select_dropdown_option` | `agentId`, `text` | Select a dropdown option by matching its display text. |

**Completion:**

| Tool Name | Parameters | Description |
| :--- | :--- | :--- |
| `done` | `text`, `success` | Signal that the agent has completed its task. |

---

## Prompt Engineering for Web Agents

When building your agent's system prompt, consider including the following instructions:

1.  **Analyze the Tree First**: "Before taking an action, scan the `accessibilityTree` or `compactTree` to find the `agentId` of the element you want to interact with."
2.  **Verify Visibility**: "Do not attempt to click elements marked as `occluded: true`. If your target is occluded, look for a way to dismiss the overlay (e.g., a 'Close' button or 'Accept Cookies')."
3.  **Use Strategic Scrolling**: "If the element you need is not in the current tree, use `scroll_to_text` to find it directly, or `scroll` / `next_page` to move through the page."
4.  **Handle Dropdowns Properly**: "For `<select>` elements, first call `get_dropdown_options` to see available choices, then call `select_dropdown_option` with the matching text."
5.  **Use Keyboard Shortcuts**: "Use `send_keys` for keyboard interactions like `Control+A` (select all), `Enter` (submit), or `Escape` (dismiss)."
6.  **Confirm Outcomes**: "After every action, call `captureState` again to verify the result of your action and see if the page has changed."
7.  **Signal Completion**: "When the task is done, call `done` with a summary of what was accomplished."

---

## Handling Complex Web States

### Page Settlement
The SDK automatically waits for a "settlement" period where the network is idle and the DOM is stable. However, modern Single Page Applications (SPAs) can be unpredictable. 
- **Best Practice**: If the agent expects a significant change (like a login redirect) but sees the same page, it should use a `wait` action followed by another `captureState`.

### Framework Compatibility
The SDK uses native event dispatching to ensure compatibility with React, Vue, and Angular. Your agent does not need to worry about the underlying JS framework; it simply sends the `input_text` command, and the SDK handles the internal event bubbling required for the framework to "see" the new value.
