package dev.shantoislam.agenticwebview.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface BrowserResult<out T> {
    @Serializable
    @SerialName("success")
    data class Success<T>(
        val value: T,
        val diagnostics: OperationDiagnostics = OperationDiagnostics(),
    ) : BrowserResult<T>

    @Serializable
    @SerialName("failure")
    data class Failure(
        val error: BrowserError,
        val diagnostics: OperationDiagnostics = OperationDiagnostics(),
    ) : BrowserResult<Nothing>
}

@Serializable
data class OperationDiagnostics(
    val operationId: String? = null,
    val durationMs: Long? = null,
    val strategy: String? = null,
    val attempts: Int = 1,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(durationMs == null || durationMs >= 0) { "durationMs must be non-negative" }
        require(attempts >= 1) { "attempts must be at least 1" }
    }
}

@Serializable
sealed interface BrowserError {
    val message: String

    @Serializable @SerialName("session_not_attached")
    data class SessionNotAttached(override val message: String = "Browser session is not attached") : BrowserError

    @Serializable @SerialName("session_closed")
    data class SessionClosed(override val message: String = "Browser session is closed") : BrowserError

    @Serializable @SerialName("navigation_blocked")
    data class NavigationBlocked(val url: String, val reason: String, override val message: String = "Navigation was blocked") : BrowserError

    @Serializable @SerialName("navigation_failed")
    data class NavigationFailed(val url: String, val httpStatus: Int? = null, override val message: String = "Navigation failed") : BrowserError

    @Serializable @SerialName("navigation_history_unavailable")
    data class NavigationHistoryUnavailable(
        val operation: NavigationOperation,
        override val message: String = "Requested browser history navigation is unavailable",
    ) : BrowserError

    @Serializable @SerialName("page_not_ready")
    data class PageNotReady(val phase: BrowserSessionPhase, override val message: String = "Page is not ready") : BrowserError

    @Serializable @SerialName("stale_element")
    data class StaleElementReference(val target: ElementRef, override val message: String = "Element reference is stale") : BrowserError

    @Serializable @SerialName("element_not_found")
    data class ElementNotFound(val target: ElementRef, override val message: String = "Element was not found") : BrowserError

    @Serializable @SerialName("element_not_actionable")
    data class ElementNotActionable(val target: ElementRef, val reason: String, override val message: String = "Element is not actionable") : BrowserError

    @Serializable @SerialName("element_occluded")
    data class ElementOccluded(val target: ElementRef, val occludedBy: ElementRef? = null, override val message: String = "Element is occluded") : BrowserError

    @Serializable @SerialName("unsupported_frame")
    data class UnsupportedFrame(val frameId: FrameId, val reason: String, override val message: String = "Frame is not supported") : BrowserError

    @Serializable @SerialName("unsupported_action")
    data class UnsupportedAction(val action: String, val reason: String, override val message: String = "Action is not supported") : BrowserError

    @Serializable @SerialName("action_rejected")
    data class ActionRejected(val action: String, val reason: String, override val message: String = "Action was rejected") : BrowserError

    @Serializable @SerialName("action_not_verified")
    data class ActionNotVerified(
        val action: String,
        val receipt: CommandReceipt? = null,
        override val message: String = "Action was dispatched but its effect could not be verified",
    ) : BrowserError

    @Serializable @SerialName("runtime_unavailable")
    data class RuntimeUnavailable(override val message: String = "Page runtime is unavailable") : BrowserError

    @Serializable @SerialName("protocol_mismatch")
    data class RuntimeProtocolMismatch(val expected: Int, val actual: Int?, override val message: String = "Runtime protocol version mismatch") : BrowserError

    @Serializable @SerialName("runtime_failure")
    data class RuntimeFailure(val code: String, override val message: String, val details: Map<String, String> = emptyMap()) : BrowserError

    @Serializable @SerialName("malformed_runtime_response")
    data class MalformedRuntimeResponse(override val message: String) : BrowserError

    @Serializable @SerialName("renderer_terminated")
    data class RendererTerminated(val didCrash: Boolean, override val message: String = "WebView renderer terminated") : BrowserError

    @Serializable @SerialName("screenshot_failure")
    data class ScreenshotFailure(override val message: String) : BrowserError

    @Serializable @SerialName("timeout")
    data class Timeout(val operation: String, val timeoutMs: Long, override val message: String = "Operation timed out") : BrowserError

    @Serializable @SerialName("cancelled")
    data class Cancelled(val operation: String, override val message: String = "Operation was cancelled") : BrowserError

    @Serializable @SerialName("resource_limit")
    data class ResourceLimitExceeded(val resource: String, val limit: Long, override val message: String = "Resource limit exceeded") : BrowserError
}
