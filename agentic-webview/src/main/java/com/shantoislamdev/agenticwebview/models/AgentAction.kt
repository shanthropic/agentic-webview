package com.shantoislamdev.agenticwebview.models

sealed class AgentAction {
    data class Click(val agentId: String) : AgentAction()
    data class LongPress(val agentId: String, val durationMs: Long = 500) : AgentAction()
    data class InputText(val agentId: String, val text: String, val clearFirst: Boolean = true) : AgentAction()
    data class SelectOption(val agentId: String, val value: String) : AgentAction()
    data class Scroll(val direction: ScrollDirection, val amount: Float = 0.5f) : AgentAction()
    data class Navigate(val url: String) : AgentAction()
    data object GoBack : AgentAction()
    data object GoForward : AgentAction()
    data object Refresh : AgentAction()
    data class Wait(val durationMs: Long = 1000) : AgentAction()
}

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
