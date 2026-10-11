# Koog agent integration

The `integration-koog` module provides an official binding between Agentic WebView's framework-neutral tools and the [Koog Agents](https://github.com/JetBrains/koog) library.

## Dependency setup

Add the integration module alongside the Koog framework dependencies in your `build.gradle.kts`:

```kotlin
// build.gradle.kts
dependencies {
    // Agentic WebView Koog adapter & core tools
    implementation("dev.shantoislam.agenticwebview:integration-koog:0.3.0")
    implementation("dev.shantoislam.agenticwebview:agent-tools:0.3.0")

    // Koog Agents framework
    implementation("ai.koog:koog-agents:1.2.0")
    implementation("ai.koog:koog-prompt-executor-llms:1.2.0")
    implementation("ai.koog:koog-prompt-executor-openai:1.2.0")
    implementation("ai.koog:koog-http-client-ktor:1.2.0")
}
```

---

## Complete Koog Agent Recipe

`KoogBrowserTools` is a reflective `ToolSet`. It exposes browser operations with unpacked method arguments (e.g. `documentId`, `frameId`, `elementId`, `observedAtRevision`) so the LLM does not need to double-encode target JSON strings.

```kotlin
package com.example.app

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.MessagePart
import dev.shantoislam.agenticwebview.api.AgenticBrowserSession
import dev.shantoislam.agenticwebview.integrations.koog.KoogBrowserTools
import dev.shantoislam.agenticwebview.tools.BrowserToolProfile
import dev.shantoislam.agenticwebview.tools.StandardBrowserTools
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

class BrowserAgentService(
    private val session: AgenticBrowserSession,
) {
    suspend fun runTask(
        apiKey: String,
        userPrompt: String,
        onStatusUpdate: (String) -> Unit,
    ): String {
        // 1. Create the framework-neutral dispatcher and wrap with KoogBrowserTools
        val dispatcher = StandardBrowserTools(
            session = session,
            profile = BrowserToolProfile.STANDARD,
        )
        val browserTools = KoogBrowserTools(dispatcher)

        // 2. Set up HTTP client and LLM executor
        val httpClientFactory = KtorKoogHttpClient.Factory(
            baseClient = HttpClient(OkHttp),
        )
        val clientSettings = OpenAIClientSettings(
            baseUrl = "https://api.openai.com",
            timeoutConfig = ConnectionTimeoutConfig(
                requestTimeoutMillis = 60_000,
                connectTimeoutMillis = 15_000,
                socketTimeoutMillis = 60_000,
            ),
        )
        val llmClient = OpenAILLMClient(
            apiKey = apiKey,
            settings = clientSettings,
            httpClientFactory = httpClientFactory,
        )
        val promptExecutor = MultiLLMPromptExecutor(llmClient)

        // 3. Register browser tools
        val toolRegistry = ToolRegistry {
            tools(browserTools)
        }

        // 4. Configure agent prompt and execution strategy
        val agentConfig = AIAgentConfig(
            prompt = prompt("browser_navigator") {
                system(
                    """
                    You are an autonomous web browsing agent.
                    1. Use 'browser_observe' to inspect the semantic page structure and locate interactive elements.
                    2. Use the element references (documentId, frameId, elementId, observedAtRevision) with actions.
                    3. After navigation or mutations, re-observe to ensure references are fresh.
                    4. Summarize your findings when the user's task is completed.
                    """.trimIndent()
                )
            },
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 25,
        )

        // 5. Build agent with event callbacks for UI feedback
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = singleRunStrategy(parallelTools = false),
            toolRegistry = toolRegistry,
        ) {
            handleEvents {
                onToolCallStarting { ctx ->
                    onStatusUpdate("Executing tool: ${ctx.toolName}...")
                }
                onToolCallCompleted { ctx ->
                    onStatusUpdate("Tool ${ctx.toolName} completed.")
                }
                onToolCallFailed { ctx ->
                    onStatusUpdate("Tool ${ctx.toolName} failed: ${ctx.error?.message ?: ctx.message}")
                }
                onLLMCallCompleted { ctx ->
                    val reasoning = ctx.response?.parts?.filterIsInstance<MessagePart.Reasoning>()
                        ?.flatMap { it.content }
                        ?.joinToString("\n")
                    if (!reasoning.isNullOrBlank()) {
                        onStatusUpdate("Reasoning: $reasoning")
                    }
                }
            }
        }

        // 6. Run the agent against the user prompt
        return agent.run(userPrompt)
    }
}
```

---

## Architectural Isolation

`KoogBrowserTools` depends exclusively on `AgentToolDispatcher`. It has zero dependency on Android or `WebView` internal packages:
- The agent framework cannot accidentally bypass navigation policies or tamper with WebView settings.
- All actions pass through the validated dispatch pipeline with timeout guards and element freshness verification.

---

## Related documentation

- [Framework-neutral agent tools](agent-tools.md)
- [JSON-RPC tool endpoint](jsonrpc.md)
- [Observation model & ElementRef](observations.md)
- [Sample Android application](../samples/android/README.md)
