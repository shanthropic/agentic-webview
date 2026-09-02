package dev.shantoislam.agenticwebview.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface AgenticBrowserSession : AutoCloseable {
    val state: StateFlow<BrowserSessionState>
    val events: Flow<BrowserEvent>

    suspend fun navigate(request: NavigationRequest): BrowserResult<NavigationReceipt>
    suspend fun navigateHistory(request: HistoryNavigationRequest): BrowserResult<NavigationReceipt>
    suspend fun observe(options: ObservationOptions = ObservationOptions()): BrowserResult<BrowserObservation>
    suspend fun execute(command: BrowserCommand): BrowserResult<CommandReceipt>
    suspend fun await(condition: WaitCondition): BrowserResult<WaitReceipt>

    override fun close()
}

@Serializable
data class BrowserSessionState(
    val phase: BrowserSessionPhase,
    val documentId: DocumentId? = null,
    val revision: ObservationRevision = ObservationRevision(0),
    val url: String? = null,
)

@Serializable
enum class BrowserSessionPhase {
    DETACHED,
    ATTACHED,
    NAVIGATING,
    DOCUMENT_CREATED,
    RUNTIME_INITIALIZING,
    INTERACTIVE,
    STABILIZING,
    READY,
    FAILED,
    RENDERER_TERMINATED,
    CLOSED,
}

@Serializable
sealed interface BrowserEvent {
    @Serializable @SerialName("state_changed")
    data class StateChanged(val previous: BrowserSessionState, val current: BrowserSessionState) : BrowserEvent

    @Serializable @SerialName("document_revision")
    data class DocumentRevisionChanged(val documentId: DocumentId, val revision: ObservationRevision) : BrowserEvent

    @Serializable @SerialName("popup_requested")
    data class PopupRequested(val url: String?, val isUserGesture: Boolean) : BrowserEvent

    @Serializable @SerialName("dialog_requested")
    data class DialogRequested(val type: DialogType, val message: String, val defaultValue: String? = null) : BrowserEvent

    @Serializable @SerialName("download_requested")
    data class DownloadRequested(val url: String, val mimeType: String?, val contentLength: Long?) : BrowserEvent

    @Serializable @SerialName("permission_requested")
    data class PermissionRequested(val origin: String, val resources: List<String>) : BrowserEvent

    @Serializable @SerialName("file_chooser_requested")
    data class FileChooserRequested(val acceptTypes: List<String>, val captureEnabled: Boolean) : BrowserEvent

    @Serializable @SerialName("navigation_blocked")
    data class NavigationBlocked(val url: String, val reason: String) : BrowserEvent

    @Serializable @SerialName("ssl_error")
    data class SslErrorReceived(val url: String, val primaryError: Int) : BrowserEvent

    @Serializable @SerialName("safe_browsing_hit")
    data class SafeBrowsingHit(val url: String, val threatType: Int) : BrowserEvent

    @Serializable @SerialName("renderer_terminated")
    data class RendererTerminated(val didCrash: Boolean) : BrowserEvent
}

@Serializable
enum class DialogType { ALERT, CONFIRM, PROMPT, BEFORE_UNLOAD }

fun interface BrowserDiagnosticsSink {
    fun emit(event: BrowserDiagnosticEvent)
}

data class BrowserDiagnosticEvent(
    val category: String,
    val operationId: String? = null,
    val documentId: DocumentId? = null,
    val revision: ObservationRevision? = null,
    val durationMs: Long? = null,
    val attributes: Map<String, String> = emptyMap(),
)
