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

    data class SendKeys(val keys: String) : AgentAction()
    data class ScrollToPercent(val yPercent: Float, val agentId: String? = null) : AgentAction()
    data class ScrollToText(val text: String, val nth: Int = 0) : AgentAction()
    data class ScrollToTop(val agentId: String? = null) : AgentAction()
    data class ScrollToBottom(val agentId: String? = null) : AgentAction()
    data class PreviousPage(val agentId: String? = null) : AgentAction()
    data class NextPage(val agentId: String? = null) : AgentAction()
    data class GetDropdownOptions(val agentId: String) : AgentAction()
    data class SelectDropdownOption(val agentId: String, val text: String) : AgentAction()
    data class Done(val text: String, val success: Boolean) : AgentAction()
}

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
