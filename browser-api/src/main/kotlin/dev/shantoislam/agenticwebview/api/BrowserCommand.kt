package dev.shantoislam.agenticwebview.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface BrowserCommand {
    @Serializable @SerialName("click")
    data class Click(val target: ElementRef, val button: PointerButton = PointerButton.PRIMARY) : BrowserCommand

    @Serializable @SerialName("long_press")
    data class LongPress(val target: ElementRef, val durationMs: Long = 500) : BrowserCommand {
        init { require(durationMs in 1..60_000) { "durationMs is out of range" } }
    }

    @Serializable @SerialName("type_text")
    data class TypeText(val target: ElementRef, val text: String, val mode: TextInputMode = TextInputMode.REPLACE_ALL) : BrowserCommand

    @Serializable @SerialName("select_option")
    data class SelectOption(val target: ElementRef, val option: SelectOptionMatcher) : BrowserCommand

    @Serializable @SerialName("press_keys")
    data class PressKeys(val chord: KeyChord) : BrowserCommand

    @Serializable @SerialName("scroll")
    data class Scroll(val target: ScrollTarget = ScrollTarget.Page, val delta: ScrollDelta) : BrowserCommand

    @Serializable @SerialName("scroll_into_view")
    data class ScrollIntoView(val target: ElementRef, val alignment: ScrollAlignment = ScrollAlignment.CENTER) : BrowserCommand
}

@Serializable
enum class PointerButton { PRIMARY, SECONDARY, MIDDLE }

@Serializable
enum class TextInputMode { REPLACE_ALL, APPEND, INSERT_AT_SELECTION, CLEAR }

@Serializable
sealed interface SelectOptionMatcher {
    @Serializable @SerialName("value") data class Value(val value: String) : SelectOptionMatcher
    @Serializable @SerialName("label") data class Label(val label: String) : SelectOptionMatcher
    @Serializable @SerialName("index") data class Index(val index: Int) : SelectOptionMatcher {
        init { require(index >= 0) { "Option index must be non-negative" } }
    }
}

@Serializable
data class KeyChord(
    val key: String,
    val control: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
) {
    init { require(key.isNotBlank()) { "Key must not be blank" } }
}

@Serializable
sealed interface ScrollTarget {
    @Serializable @SerialName("page") data object Page : ScrollTarget
    @Serializable @SerialName("element") data class Element(val target: ElementRef) : ScrollTarget
}

@Serializable
data class ScrollDelta(val xCssPx: Double = 0.0, val yCssPx: Double = 0.0) {
    init { require(xCssPx.isFinite() && yCssPx.isFinite()) { "Scroll delta must be finite" } }
}

@Serializable
enum class ScrollAlignment { START, CENTER, END, NEAREST }

@Serializable
data class NavigationRequest(
    val url: String,
    val readiness: WaitCondition = WaitCondition.PageReady,
) {
    init { require(url.isNotBlank()) { "Navigation URL must not be blank" } }
}

@Serializable
data class NavigationReceipt(
    val operation: NavigationOperation,
    val requestedUrl: String? = null,
    val finalUrl: String,
    val documentId: DocumentId,
    val phase: BrowserSessionPhase,
)

@Serializable
data class HistoryNavigationRequest(
    val operation: NavigationOperation,
    val readiness: WaitCondition = WaitCondition.PageReady,
) {
    init {
        require(operation != NavigationOperation.URL) {
            "HistoryNavigationRequest cannot use NavigationOperation.URL"
        }
    }
}

@Serializable
enum class NavigationOperation { URL, BACK, FORWARD, RELOAD }

@Serializable
data class CommandReceipt(
    val commandType: String,
    val strategy: ActionExecutionStrategy,
    val documentId: DocumentId,
    val revisionBefore: ObservationRevision,
    val revisionAfter: ObservationRevision,
    val dispatched: Boolean,
    val verified: Boolean,
    val pageChanged: Boolean,
)

@Serializable
enum class ActionExecutionStrategy {
    DOM_POINTER,
    DOM_NATIVE_SETTER,
    DOM_SELECT,
    DOM_KEYBOARD,
    DOM_SCROLL,
    ANDROID_NATIVE_POINTER,
}

@Serializable
sealed interface WaitCondition {
    @Serializable @SerialName("page_ready") data object PageReady : WaitCondition
    @Serializable @SerialName("dom_quiet") data class DomQuiet(val quietWindowMs: Long = 500) : WaitCondition {
        init { require(quietWindowMs in 1..60_000) { "quietWindowMs is out of range" } }
    }
    @Serializable @SerialName("element_present") data class ElementPresent(val target: ElementRef) : WaitCondition
}

@Serializable
data class WaitReceipt(
    val conditionType: String,
    val satisfiedAtEpochMs: Long,
    val documentId: DocumentId,
    val revision: ObservationRevision,
)
