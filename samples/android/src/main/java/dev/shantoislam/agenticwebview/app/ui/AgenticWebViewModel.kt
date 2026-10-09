package dev.shantoislam.agenticwebview.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.shantoislam.agenticwebview.api.AgenticBrowserSession
import dev.shantoislam.agenticwebview.app.agent.AgentRunner
import dev.shantoislam.agenticwebview.app.agent.BrowserAgentTools
import dev.shantoislam.agenticwebview.app.agent.SimulationRunner
import dev.shantoislam.agenticwebview.app.data.AgentSettingsDao
import dev.shantoislam.agenticwebview.app.data.AgentSettingsEntity
import dev.shantoislam.agenticwebview.app.ui.components.ToolStatus
import dev.shantoislam.agenticwebview.tools.AgentToolDispatcher
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ChatItem {
    val id: String
    val timestamp: Long

    data class User(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatItem

    data class Assistant(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatItem

    data class ToolCall(
        override val id: String = UUID.randomUUID().toString(),
        val toolName: String,
        val argsJson: String,
        val status: ToolStatus,
        val resultJson: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatItem
}

class AgenticWebViewModel(
    private val settingsDao: AgentSettingsDao,
) : ViewModel() {

    val settings: StateFlow<AgentSettingsEntity> = settingsDao.getSettings()
        .map { it ?: AgentSettingsEntity() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AgentSettingsEntity(),
        )

    private val _messages = MutableStateFlow<List<ChatItem>>(
        listOf(
            ChatItem.Assistant(
                text = "Hello! I am your Autonomous WebView Agent. Ask me to navigate, search, inspect page content, or click elements.",
            )
        )
    )
    val messages: StateFlow<List<ChatItem>> = _messages.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _thought = MutableStateFlow("")
    val thought: StateFlow<String> = _thought.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            if (settingsDao.getSettingsOnce() == null) {
                settingsDao.saveSettings(AgentSettingsEntity())
            }
        }
    }

    fun onDraftChange(newDraft: String) {
        _draft.value = newDraft
    }

    fun saveSettings(newSettings: AgentSettingsEntity) {
        viewModelScope.launch {
            settingsDao.saveSettings(newSettings)
        }
    }

    fun sendPrompt(
        promptText: String,
        session: AgenticBrowserSession,
        dispatcher: AgentToolDispatcher,
    ) {
        if (promptText.isBlank() || _isWorking.value) return

        _draft.value = ""
        val userMsg = ChatItem.User(text = promptText)
        _messages.value = _messages.value + userMsg

        activeJob = viewModelScope.launch {
            _isWorking.value = true
            _thought.value = ""
            val currentSettings = settings.value

            try {
                val response = if (currentSettings.useSimulation) {
                    val simRunner = SimulationRunner(session, dispatcher)
                    simRunner.runSimulation(
                        userPrompt = promptText,
                        onToolStarted = { id, name, args -> handleToolStarted(id, name, args) },
                        onToolCompleted = { id, name, res -> handleToolCompleted(id, name, res) },
                        onToolFailed = { id, name, err -> handleToolFailed(id, name, err) },
                        onThinkingChanged = { thinking -> _isThinking.value = thinking },
                        onThoughtUpdate = { thoughtText -> _thought.value = thoughtText },
                    )
                } else {
                    if (currentSettings.apiKey.isBlank()) {
                        "Please provide your OpenAI API key in Settings (gear icon in the top bar) or enable Simulation Mode to test offline."
                    } else {
                        val browserTools = BrowserAgentTools(dispatcher)
                        val agentRunner = AgentRunner(browserTools)
                        agentRunner.runAgent(
                            settings = currentSettings,
                            userPrompt = promptText,
                            onToolStarted = { id, name, args -> handleToolStarted(id, name, args) },
                            onToolCompleted = { id, name, res -> handleToolCompleted(id, name, res) },
                            onToolFailed = { id, name, err -> handleToolFailed(id, name, err) },
                            onThinkingChanged = { thinking -> _isThinking.value = thinking },
                            onThoughtUpdate = { thoughtText -> _thought.value = thoughtText },
                        )
                    }
                }

                _messages.value = _messages.value + ChatItem.Assistant(text = response)
            } catch (e: CancellationException) {
                markRunningToolsAsCancelled()
                _messages.value = _messages.value + ChatItem.Assistant(text = "Agent execution was cancelled.")
            } catch (e: Exception) {
                markRunningToolsAsCancelled()
                _messages.value = _messages.value + ChatItem.Assistant(text = "Agent error: ${e.message ?: "Unknown error"}")
            } finally {
                _isWorking.value = false
                _isThinking.value = false
                activeJob = null
            }
        }
    }

    fun stopAgent() {
        activeJob?.cancel()
        activeJob = null
        markRunningToolsAsCancelled()
        _isWorking.value = false
        _isThinking.value = false
    }

    private fun markRunningToolsAsCancelled() {
        val list = _messages.value.map { item ->
            if (item is ChatItem.ToolCall && item.status == ToolStatus.RUNNING) {
                item.copy(status = ToolStatus.FAILURE, resultJson = "Execution cancelled")
            } else {
                item
            }
        }
        _messages.value = list
    }

    fun handleToolStarted(id: String, name: String, args: String) {
        val toolMsg = ChatItem.ToolCall(
            id = id,
            toolName = name,
            argsJson = args,
            status = ToolStatus.RUNNING,
        )
        _messages.value = _messages.value + toolMsg
    }

    fun handleToolCompleted(id: String, name: String, result: String) {
        val list = _messages.value.toMutableList()
        val index = list.indexOfLast { it is ChatItem.ToolCall && it.id == id }
        if (index != -1) {
            val old = list[index] as ChatItem.ToolCall
            val isError = result.contains("\"status\": \"error\"") ||
                result.contains("\"status\":\"error\"")
            list[index] = old.copy(
                status = if (isError) ToolStatus.FAILURE else ToolStatus.SUCCESS,
                resultJson = result,
            )
            _messages.value = list
        }
    }

    fun handleToolFailed(id: String, name: String, error: String) {
        val list = _messages.value.toMutableList()
        val index = list.indexOfLast { it is ChatItem.ToolCall && it.id == id }
        if (index != -1) {
            val old = list[index] as ChatItem.ToolCall
            list[index] = old.copy(status = ToolStatus.FAILURE, resultJson = error)
            _messages.value = list
        }
    }

    class Factory(private val settingsDao: AgentSettingsDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AgenticWebViewModel(settingsDao) as T
        }
    }
}
