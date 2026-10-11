# Sample Android Application

This module demonstrates integrating Agentic WebView into an Android application with Jetpack Compose, supporting both **Offline Simulation Mode** and **Autonomous Agent Mode** (via the JetBrains Koog framework).

---

## Architecture Overview

```
samples/android/src/main/java/dev/shantoislam/agenticwebview/app/
├── MainActivity.kt                  // ComponentActivity hosting MainScreen
├── agent/
│   ├── BrowserAgentTools.kt        // Koog ToolSet wrapping StandardBrowserTools
│   ├── SimulationRunner.kt         // Deterministic heuristic agent (Offline Mode)
│   └── AgentRunner.kt              // Live LLM agent powered by Koog & OpenAI (Autonomous Mode)
├── data/
│   ├── AppDatabase.kt              // Room database for persistent agent settings
│   ├── AgentSettingsEntity.kt      // Entity holding API Key, Base URL, Model Name
│   └── AgentSettingsDao.kt         // DAO for settings storage
└── ui/
    ├── AgenticWebViewModel.kt      // Coordinates browser host, agent runners, and chat state
    ├── MainScreen.kt               // Split UI: browser viewport on top, agent chat on bottom
    ├── components/
    │   ├── BrowserTopBar.kt        // URL bar, refresh, phase indicator, settings button
    │   ├── AgentComposer.kt        // User prompt input field and send button
    │   ├── ChatMessageBubble.kt    // Chat bubbles for user and assistant messages
    │   ├── ToolCallCard.kt         // Expandable cards showing tool execution and JSON results
    │   ├── ReasoningSection.kt     // Expandable card displaying model reasoning thought process
    │   └── SettingsSheet.kt        // Modal bottom sheet to configure LLM API keys
    └── theme/                      // Material3 theme, typography, and color palette
```

---

## Operating Modes

### 1. Offline Simulation Mode (`SimulationRunner`)
- **No API key or network LLM connection required**.
- Operates automatically when no API key is configured.
- Exercises `StandardBrowserTools` via deterministic heuristics:
  - Detects keywords in user prompt (e.g., `"duckduckgo"`, `"google"`, `"scroll"`, `"inspect"`).
  - Emits real multi-step tool calls: `browser_navigate`, `browser_observe`, `browser_scroll`.
  - Simulates LLM thinking intervals (`onThinkingChanged`, `onThoughtUpdate`) and reports tool start/completion/failure to the UI in real time.
- Ideal for offline UI testing, emulator demonstrations, and validating SDK tool execution.

### 2. Autonomous Agent Mode (`AgentRunner`)
- **Live LLM integration powered by JetBrains Koog v1.2.0**.
- Configured in the in-app **Settings Sheet** (`SettingsSheet.kt`):
  - **API Key**: OpenAI API Key (or compatible provider).
  - **Base URL**: Defaults to `https://api.openai.com`, or any OpenAI-compatible endpoint (e.g. Ollama, OpenRouter, vLLM).
  - **Model Name**: Defaults to `gpt-4o`.
- Uses `BrowserAgentTools` to expose browser operations to the model.
- Parses streaming reasoning thoughts (`MessagePart.Reasoning`) into the UI reasoning display.
- Executes iterative observe-plan-act-verify loops until the user's objective is completed.

---

## How to Build and Run

### Build debug APK:
```bash
./gradlew :samples:android:assembleDebug
```

### Install and run on connected device / emulator:
```bash
./gradlew :samples:android:installDebug
```

---

## Unit Test Suite

The sample app includes automated unit tests covering the ViewModel and agent runners:

```bash
./gradlew :samples:android:testDebugUnitTest
```

- **`SimulationRunnerTest`**: Verifies deterministic multi-step tool execution, scroll detection, DOM inspection, and navigation flows.
- **`BrowserAgentToolsTest`**: Tests reflection and JSON argument serialization through `BrowserAgentTools`.
- **`AgenticWebViewModelTest`**: Tests UI state transitions, tool call tracking, and chat message history.

---

## Documentation Links

- [Sample Application Documentation](../../docs/samples.md)
- [Getting Started Guide](../../docs/getting-started.md)
- [Koog Agent Integration](../../docs/koog.md)
- [Framework-Neutral Agent Tools](../../docs/agent-tools.md)
