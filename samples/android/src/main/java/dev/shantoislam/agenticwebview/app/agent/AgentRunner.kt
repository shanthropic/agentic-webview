package dev.shantoislam.agenticwebview.app.agent

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
import dev.shantoislam.agenticwebview.app.data.AgentSettingsEntity
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

class AgentRunner(
    private val browserTools: BrowserAgentTools,
) {
    suspend fun runAgent(
        settings: AgentSettingsEntity,
        userPrompt: String,
        onToolStarted: (id: String, name: String, args: String) -> Unit,
        onToolCompleted: (id: String, name: String, result: String) -> Unit,
        onToolFailed: (id: String, name: String, error: String) -> Unit,
        onThinkingChanged: (Boolean) -> Unit,
        onThoughtUpdate: (String) -> Unit,
    ): String {
        val httpFactory = KtorKoogHttpClient.Factory(
            baseClient = HttpClient(OkHttp),
        )
        val timeout = ConnectionTimeoutConfig(
            requestTimeoutMillis = 120_000,
            connectTimeoutMillis = 30_000,
            socketTimeoutMillis = 120_000,
        )
        val clientSettings = OpenAIClientSettings(
            baseUrl = settings.baseUrl.ifBlank { "https://api.openai.com" },
            timeoutConfig = timeout,
        )
        val llmClient = OpenAILLMClient(
            apiKey = settings.apiKey,
            settings = clientSettings,
            httpClientFactory = httpFactory,
        )
        val promptExecutor = MultiLLMPromptExecutor(llmClient)
        val model = OpenAIModels.Chat.GPT4o.copy(id = settings.modelName.ifBlank { "gpt-4o" })

        val toolRegistry = ToolRegistry {
            tools(browserTools)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("browser_agent") {
                system(
                    """
                    You are an autonomous web browsing agent.
                    You have access to browser tools to observe and interact with the webpage.
                    1. Begin by calling 'browser_observe' to inspect the semantic page tree and find element references.
                    2. Use element references (documentId, frameId, elementId, observedAtRevision) with 'browser_click', 'browser_type_text', or 'browser_select_option'.
                    3. After taking an action that modifies the page or triggers navigation, call 'browser_observe' again to inspect updated state.
                    4. Once you have completed the user's objective, provide a clear, helpful response explaining what you found or accomplished.
                    """.trimIndent()
                )
            },
            model = model,
            maxAgentIterations = 50,
        )

        val agent = AIAgent(
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            strategy = singleRunStrategy(parallelTools = false),
            toolRegistry = toolRegistry,
        ) {
            handleEvents {
                onToolCallStarting { ctx ->
                    val callId = ctx.toolCallId ?: ctx.eventId
                    onToolStarted(callId, ctx.toolName, ctx.toolArgs.toString())
                }
                onToolCallCompleted { ctx ->
                    val callId = ctx.toolCallId ?: ctx.eventId
                    onToolCompleted(callId, ctx.toolName, ctx.toolResult?.toString() ?: "{}")
                }
                onToolCallFailed { ctx ->
                    val callId = ctx.toolCallId ?: ctx.eventId
                    onToolFailed(callId, ctx.toolName, ctx.message.ifBlank { ctx.error?.message ?: "Tool call failed" })
                }
                onToolValidationFailed { ctx ->
                    val callId = ctx.toolCallId ?: ctx.eventId
                    onToolFailed(callId, ctx.toolName, ctx.message.ifBlank { ctx.error.message ?: "Tool validation failed" })
                }
                onLLMCallStarting {
                    onThinkingChanged(true)
                }
                onLLMCallCompleted { ctx ->
                    val reasoning = ctx.response?.parts?.filterIsInstance<MessagePart.Reasoning>()
                        ?.flatMap { it.content }
                        ?.joinToString("\n")
                    if (!reasoning.isNullOrBlank()) {
                        onThoughtUpdate(reasoning)
                    }
                    onThinkingChanged(false)
                }
            }
        }

        return try {
            agent.run(userPrompt)
        } finally {
            onThinkingChanged(false)
        }
    }
}
