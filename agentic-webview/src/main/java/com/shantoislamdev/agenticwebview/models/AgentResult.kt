package com.shantoislamdev.agenticwebview.models

sealed class AgentResult<out T> {
    data class Success<T>(val data: T) : AgentResult<T>()
    data class Error(val error: AgentError) : AgentResult<Nothing>()
}

sealed class AgentError {
    data class JsEvaluationTimeout(val timeoutMs: Long) : AgentError()
    data class JsEvaluationFailed(val message: String) : AgentError()
    data class ElementNotFound(val agentId: String) : AgentError()
    data class ElementOccluded(val agentId: String, val occludedBy: String?) : AgentError()
    data class NavigationFailed(val url: String, val httpCode: Int?) : AgentError()
    data class WebViewCrashed(val didRecover: Boolean) : AgentError()
    data class ScreenshotFailed(val reason: String) : AgentError()
    data class PageNotReady(val currentState: PageLifecycleState) : AgentError()
    data class Timeout(val operation: String, val timeoutMs: Long) : AgentError()
    data class FileUploaderDetected(val agentId: String) : AgentError()
    data class NoNavigationHistory(val direction: String) : AgentError()
}
