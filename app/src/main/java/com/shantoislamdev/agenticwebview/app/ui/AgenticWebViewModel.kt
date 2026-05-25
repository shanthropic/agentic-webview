package com.shantoislamdev.agenticwebview.app.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewModelScope
import com.shantoislamdev.agenticwebview.AgenticWebController
import com.shantoislamdev.agenticwebview.config.AgenticWebViewConfig
import com.shantoislamdev.agenticwebview.app.agent.*
import com.shantoislamdev.agenticwebview.app.model.AgentSettingsEntity
import com.shantoislamdev.agenticwebview.app.model.AppDatabase
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistryBuilder
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLMCapability
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class ChatMessage(
    val sender: String,
    val message: String,
    val isUser: Boolean
)

data class AgentSettings(
    val useSimulation: Boolean = true,
    val baseUrl: String = "https://api.openai.com",
    val apiKey: String = "",
    val modelName: String = "gpt-4-turbo"
)

fun AgentSettings.toEntity() = AgentSettingsEntity(
    useSimulation = useSimulation,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName
)

fun AgentSettingsEntity.toDomain() = AgentSettings(
    useSimulation = useSimulation,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName
)

class AgenticWebViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.agentSettingsDao()

    val controller = AgenticWebController(AgenticWebViewConfig())
    
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> = _messages

    private val _settings = MutableStateFlow(AgentSettings())
    val settings: StateFlow<AgentSettings> = _settings.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    init {
        _messages.add(ChatMessage("Agent", "Hello! I'm your Agentic WebView assistant. How can I help you today?", false))
        
        viewModelScope.launch {
            dao.getSettings().collectLatest { entity ->
                entity?.let {
                    _settings.value = it.toDomain()
                }
            }
        }
    }

    fun updateSettings(newSettings: AgentSettings) {
        viewModelScope.launch {
            dao.insertSettings(newSettings.toEntity())
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        _messages.add(ChatMessage("User", text, true))
        
        viewModelScope.launch {
            _isThinking.value = true
            try {
                if (_settings.value.useSimulation) {
                    runSimulation(text)
                } else {
                    runLiveAgent(text)
                }
            } catch (e: Exception) {
                _messages.add(ChatMessage("Agent", "Error: ${e.message}", false))
            } finally {
                _isThinking.value = false
            }
        }
    }

    private suspend fun runSimulation(text: String) {
        delay(1000)
        _messages.add(ChatMessage("Agent", "Thinking about: $text", false))
        
        if (text.contains("google", ignoreCase = true)) {
            _messages.add(ChatMessage("Agent", "Navigating to Google...", false))
            controller.executeAction(com.shantoislamdev.agenticwebview.models.AgentAction.Navigate("https://www.google.com"))
        } else if (text.contains("search", ignoreCase = true)) {
             _messages.add(ChatMessage("Agent", "Searching...", false))
        } else {
            _messages.add(ChatMessage("Agent", "I'm in simulation mode. Try asking to go to google.", false))
        }
    }

    private suspend fun runLiveAgent(text: String) {
        val currentSettings = _settings.value
        if (currentSettings.apiKey.isBlank()) {
            _messages.add(ChatMessage("Agent", "Please set an API Key in settings.", false))
            return
        }

        _messages.add(ChatMessage("Agent", "Connecting to live agent (Koog)...", false))
        
        val tools = AgenticWebviewTools(controller)
        val registry = ToolRegistryBuilder().apply {
            tools(AgenticWebviewTools::class.asTools(tools))
        }.build()

        // Configure custom OpenAI client for custom base URL support
        val clientSettings = OpenAIClientSettings(
            baseUrl = currentSettings.baseUrl
        )
        val llmClient = OpenAILLMClient(currentSettings.apiKey, clientSettings)
        val promptExecutor = MultiLLMPromptExecutor(llmClient)
        
        // Define custom model with hardcoded constraints: 128k context, 64k max output
        val customModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = currentSettings.modelName,
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.Completion,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.Vision.Image
            ),
            contextLength = 128000L,
            maxOutputTokens = 64000L
        )
        
        val agent = AIAgent<String, String>(
            promptExecutor = promptExecutor,
            llmModel = customModel,
            strategy = singleRunStrategy(),
            systemPrompt = """
                You are a web browsing agent. Use the provided tools to interact with the webview.
                Always start by calling 'webview_get_state' to see what's on the page.
                When you are done or have found the answer, speak to the user.
            """.trimIndent(),
            toolRegistry = registry
        )

        try {
            val response = agent.run(text)
            _messages.add(ChatMessage("Agent", response.toString(), false))
        } catch (e: Exception) {
            _messages.add(ChatMessage("Agent", "Koog Error: ${e.message}", false))
        }
    }
}
