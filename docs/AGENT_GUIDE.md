# Developer Guide: Building AI Agents with Agentic WebView

This document provides guidance for developers building Large Language Model (LLM) based agents that utilize the Agentic WebView SDK. It explains how to interpret the SDK's output and how to effectively command the agent to browse the web.

## Perception: The "Eyes" of the Agent

Traditional web agents often struggle with raw HTML due to token limits, noise (scripts, styles), and complex layouts. This SDK provides a refined "Perception Layer" designed specifically for LLMs.

### The Accessibility Tree
The SDK parses the DOM into a simplified **Accessibility Tree**. Instead of thousands of lines of HTML, your agent receives a structured JSON array of only the interactive elements.

**Why this is better for LLMs:**
- **Token Efficiency**: Reduces the input size by 90-95% compared to raw HTML.
- **Stable Identifiers**: Each element is assigned a stable `agentId`. The LLM refers to this ID when it wants to interact.
- **Semantic Context**: Elements are enriched with roles (`button`, `link`, `input`), labels, and descriptions.
- **Occlusion Awareness**: The `occluded: true` flag tells the agent if an element is technically in the DOM but hidden behind something else (like a cookie banner or a modal), preventing the agent from trying to click unreachable targets.

### Multimodal Vision
For vision-capable models (like GPT-4o or Claude 3.5 Sonnet), the SDK provides a high-quality base64-encoded JPEG screenshot of the current viewport. 
- **Coordinate Sync**: The coordinates in the accessibility tree are perfectly synced with the screenshot, allowing the model to "see" exactly what it is "touching."

---

## Action: The "Hands" of the Agent

The SDK provides a set of deterministic tools. Your agent should be configured with a tool-calling interface that maps directly to the `AgentAction` sealed class.

### Recommended Tool Schema
Map your LLM's functions to these SDK actions:

| Tool Name | Parameters | Description |
| :--- | :--- | :--- |
| `click` | `agentId` | Taps on the element. |
| `input_text` | `agentId`, `text` | Fuses focusing, clearing, and typing into one reliable operation. |
| `scroll` | `direction`, `amount` | Direction: `UP`, `DOWN`, `LEFT`, `RIGHT`. Amount: `0.0` to `1.0`. |
| `navigate` | `url` | Direct jump to a new page. |
| `wait` | `durationMs` | Useful when the agent expects an animation or a slow network update. |

---

## Prompt Engineering for Web Agents

When building your agent's system prompt, consider including the following instructions:

1.  **Analyze the Tree First**: "Before taking an action, scan the `accessibilityTree` to find the `agentId` of the element you want to interact with."
2.  **Verify Visibility**: "Do not attempt to click elements marked as `occluded: true`. If your target is occluded, look for a way to dismiss the overlay (e.g., a 'Close' button or 'Accept Cookies')."
3.  **Use Strategic Scrolling**: "If the element you need is not in the current tree, use the `scroll` tool to move down the page."
4.  **Confirm Outcomes**: "After every action, call `captureState` again to verify the result of your action and see if the page has changed."

---

## Handling Complex Web States

### Page Settlement
The SDK automatically waits for a "settlement" period where the network is idle and the DOM is stable. However, modern Single Page Applications (SPAs) can be unpredictable. 
- **Best Practice**: If the agent expects a significant change (like a login redirect) but sees the same page, it should use a `wait` action followed by another `captureState`.

### Framework Compatibility
The SDK uses native event dispatching to ensure compatibility with React, Vue, and Angular. Your agent does not need to worry about the underlying JS framework; it simply sends the `input_text` command, and the SDK handles the internal event bubbling required for the framework to "see" the new value.
