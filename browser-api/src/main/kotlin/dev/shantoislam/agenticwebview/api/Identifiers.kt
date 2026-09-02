package dev.shantoislam.agenticwebview.api

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class DocumentId(val value: String) {
    init { require(value.isNotBlank()) { "DocumentId must not be blank" } }
}

@Serializable
@JvmInline
value class FrameId(val value: String) {
    init { require(value.isNotBlank()) { "FrameId must not be blank" } }

    companion object {
        val Main = FrameId("main")
    }
}

@Serializable
@JvmInline
value class ElementId(val value: String) {
    init { require(value.isNotBlank()) { "ElementId must not be blank" } }
}

@Serializable
@JvmInline
value class ObservationId(val value: String) {
    init { require(value.isNotBlank()) { "ObservationId must not be blank" } }
}

@Serializable
@JvmInline
value class ObservationRevision(val value: Long) {
    init { require(value >= 0) { "ObservationRevision must be non-negative" } }
}

@Serializable
data class ElementRef(
    val documentId: DocumentId,
    val frameId: FrameId,
    val elementId: ElementId,
    val observedAtRevision: ObservationRevision,
)
