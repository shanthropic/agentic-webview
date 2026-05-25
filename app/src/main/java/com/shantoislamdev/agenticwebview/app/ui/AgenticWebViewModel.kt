package com.shantoislamdev.agenticwebview.app.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shantoislamdev.agenticwebview.AgenticWebController
import com.shantoislamdev.agenticwebview.config.AgenticWebViewConfig
import com.shantoislamdev.agenticwebview.app.agent.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class ChatMessage(
    val sender: String,
    val message: String,
    val isUser: Boolean
)

data class AgentSettings(
    val useSimulation: Boolean = true,
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val modelName: String = "gpt-4-turbo"
)

class AgenticWebViewModel : ViewModel() {
    val controller = AgenticWebController(AgenticWebViewConfig())
    
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> = _messages

    private val _settings = MutableStateFlow(AgentSettings())
    val settings: StateFlow<AgentSettings> = _settings.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    init {
        _messages.add(ChatMessage("Agent", "Hello! I'm your Agentic WebView assistant. How can I help you today?", false))
    }

    fun updateSettings(newSettings: AgentSettings) {
        _settings.value = newSettings
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        _messages.add(ChatMessage("User", text, true))
        
        viewModelScope.launch {
            _isThinking.value = true
            if (_settings.value.useSimulation) {
                runSimulation(text)
            } else {
                runLiveAgent(text)
            }
            _isThinking.value = false
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
        _messages.add(ChatMessage("Agent", "Connecting to live agent...", false))
        delay(2000)
        _messages.add(ChatMessage("Agent", "Live agent mode active (Mocked loop)", false))
    }
}
