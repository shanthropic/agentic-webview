package dev.shantoislam.agenticwebview.api

import kotlinx.serialization.Serializable

@Serializable
data class BrowserObservation(
    val id: ObservationId,
    val capturedAtEpochMs: Long,
    val contentTrust: ObservationContentTrust = ObservationContentTrust.UNTRUSTED_WEBPAGE,
    val document: PageDocument,
    val revision: ObservationRevision,
    val viewport: BrowserViewport,
    val frames: List<PageFrame>,
    val nodes: List<PageNode>,
    val compactText: String,
    val screenshot: BrowserScreenshot? = null,
    val truncation: ObservationTruncation? = null,
    val warnings: List<ObservationWarning> = emptyList(),
    val metrics: ObservationMetrics = ObservationMetrics(),
) {
    init { require(capturedAtEpochMs >= 0) { "capturedAtEpochMs must be non-negative" } }
}

@Serializable
enum class ObservationContentTrust { UNTRUSTED_WEBPAGE }

@Serializable
data class PageDocument(
    val id: DocumentId,
    val url: String,
    val title: String,
    val phase: BrowserSessionPhase,
)

@Serializable
data class BrowserViewport(
    val scrollXCssPx: Double,
    val scrollYCssPx: Double,
    val widthCssPx: Double,
    val heightCssPx: Double,
    val contentWidthCssPx: Double,
    val contentHeightCssPx: Double,
    val devicePixelRatio: Double,
    val visualViewportScale: Double,
)

@Serializable
data class PageFrame(
    val id: FrameId,
    val parentId: FrameId?,
    val documentId: DocumentId,
    val url: String?,
    val origin: String?,
    val depth: Int,
    val capability: FrameCapability,
) {
    init { require(depth >= 0) { "Frame depth must be non-negative" } }
}

@Serializable
enum class FrameCapability {
    OBSERVABLE_AND_ACTIONABLE,
    OBSERVABLE_ONLY,
    INACCESSIBLE_CROSS_ORIGIN,
    SANDBOX_RESTRICTED,
    DETACHED,
}

@Serializable
data class PageNode(
    val nodeId: String,
    val parentNodeId: String?,
    val frameId: FrameId,
    val depth: Int,
    val kind: PageNodeKind,
    val tagName: String?,
    val role: String?,
    val text: String?,
    val accessibleName: String?,
    val accessibleDescription: String?,
    val attributes: Map<String, String> = emptyMap(),
    val states: Set<PageNodeState> = emptySet(),
    val bounds: ElementBounds? = null,
    val visibility: ElementVisibility = ElementVisibility.UNKNOWN,
    val elementRef: ElementRef? = null,
) {
    init {
        require(nodeId.isNotBlank()) { "nodeId must not be blank" }
        require(depth >= 0) { "Node depth must be non-negative" }
    }
}

@Serializable
enum class PageNodeKind { DOCUMENT, LANDMARK, HEADING, TEXT, LINK, CONTROL, LIST, LIST_ITEM, TABLE, ROW, CELL, IMAGE, FRAME, OTHER }

@Serializable
enum class PageNodeState { CHECKED, SELECTED, EXPANDED, COLLAPSED, DISABLED, READ_ONLY, REQUIRED, FOCUSED, EDITABLE, MULTISELECTABLE }

@Serializable
data class ElementBounds(
    val leftCssPx: Double,
    val topCssPx: Double,
    val widthCssPx: Double,
    val heightCssPx: Double,
)

@Serializable
enum class ElementVisibility { VISIBLE, OFFSCREEN, OCCLUDED, HIDDEN, UNKNOWN }

@Serializable
data class BrowserScreenshot(
    val bytes: ByteArray,
    val mimeType: String,
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(bytes.isNotEmpty()) { "Screenshot bytes must not be empty" }
        require(mimeType.isNotBlank()) { "Screenshot MIME type must not be blank" }
        require(widthPx > 0 && heightPx > 0) { "Screenshot dimensions must be positive" }
    }

    override fun equals(other: Any?): Boolean =
        other is BrowserScreenshot &&
            bytes.contentEquals(other.bytes) &&
            mimeType == other.mimeType &&
            widthPx == other.widthPx &&
            heightPx == other.heightPx

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + widthPx
        result = 31 * result + heightPx
        return result
    }
}

@Serializable
data class ObservationTruncation(
    val reason: ObservationTruncationReason,
    val limit: Long,
    val observed: Long,
)

@Serializable
enum class ObservationTruncationReason { VISITED_NODES, EMITTED_NODES, TOTAL_TEXT, TRAVERSAL_TIME, MESSAGE_SIZE }

@Serializable
data class ObservationWarning(val code: String, val message: String)

@Serializable
data class ObservationMetrics(
    val durationMs: Long = 0,
    val visitedNodeCount: Int = 0,
    val emittedNodeCount: Int = 0,
    val textCharacterCount: Int = 0,
    val encodedByteCount: Int = 0,
) {
    init {
        require(durationMs >= 0) { "durationMs must be non-negative" }
        require(visitedNodeCount >= 0 && emittedNodeCount >= 0 && textCharacterCount >= 0 && encodedByteCount >= 0) {
            "Observation metrics must be non-negative"
        }
    }
}

@Serializable
data class ObservationOptions(
    val includeScreenshot: Boolean = false,
    val includeOffscreenContent: Boolean = false,
    val includeCompactText: Boolean = true,
)

interface BrowserScreenshotProvider : AutoCloseable {
    suspend fun capture(): BrowserResult<BrowserScreenshot>
    override fun close() = Unit
}
