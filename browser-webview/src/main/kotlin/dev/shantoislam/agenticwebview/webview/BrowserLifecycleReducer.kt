package dev.shantoislam.agenticwebview.webview

import dev.shantoislam.agenticwebview.api.BrowserSessionPhase
import dev.shantoislam.agenticwebview.api.BrowserSessionState

internal object BrowserLifecycleReducer {
    fun reduce(current: BrowserSessionState, proposed: BrowserSessionState): LifecycleReduction {
        if (current.phase == proposed.phase) return LifecycleReduction.Accept(proposed)
        if (proposed.phase in ALLOWED_TRANSITIONS.getValue(current.phase)) {
            return LifecycleReduction.Accept(proposed)
        }
        return LifecycleReduction.Reject(
            "Invalid browser lifecycle transition: ${current.phase} -> ${proposed.phase}",
        )
    }

    private val ALLOWED_TRANSITIONS = mapOf(
        BrowserSessionPhase.DETACHED to setOf(
            BrowserSessionPhase.ATTACHED,
            BrowserSessionPhase.CLOSED,
        ),
        BrowserSessionPhase.ATTACHED to setOf(
            BrowserSessionPhase.NAVIGATING,
            BrowserSessionPhase.DOCUMENT_CREATED,
            BrowserSessionPhase.FAILED,
            BrowserSessionPhase.RENDERER_TERMINATED,
            BrowserSessionPhase.CLOSED,
        ),
        BrowserSessionPhase.NAVIGATING to setOf(
            BrowserSessionPhase.DOCUMENT_CREATED,
            BrowserSessionPhase.FAILED,
            BrowserSessionPhase.RENDERER_TERMINATED,
            BrowserSessionPhase.CLOSED,
        ),
        BrowserSessionPhase.DOCUMENT_CREATED to setOf(
            BrowserSessionPhase.NAVIGATING,
            BrowserSessionPhase.RUNTIME_INITIALIZING,
            BrowserSessionPhase.FAILED,
            BrowserSessionPhase.RENDERER_TERMINATED,
            BrowserSessionPhase.CLOSED,
        ),
        BrowserSessionPhase.RUNTIME_INITIALIZING to setOf(
            BrowserSessionPhase.NAVIGATING,
            BrowserSessionPhase.DOCUMENT_CREATED,
            BrowserSessionPhase.INTERACTIVE,
            BrowserSessionPhase.FAILED,
            BrowserSessionPhase.RENDERER_TERMINATED,
            BrowserSessionPhase.CLOSED,
        ),
        BrowserSessionPhase.INTERACTIVE to setOf(
            BrowserSessionPhase.NAVIGATING,
            BrowserSessionPhase.DOCUMENT_CREATED,
            BrowserSessionPhase.STABILIZING,
            BrowserSessionPhase.FAILED,
            BrowserSessionPhase.RENDERER_TERMINATED,
            BrowserSessionPhase.CLOSED,
        ),
        BrowserSessionPhase.STABILIZING to setOf(
            BrowserSessionPhase.NAVIGATING,
            BrowserSessionPhase.DOCUMENT_CREATED,
            BrowserSessionPhase.READY,
            BrowserSessionPhase.FAILED,
            BrowserSessionPhase.RENDERER_TERMINATED,
            BrowserSessionPhase.CLOSED,
        ),
        BrowserSessionPhase.READY to setOf(
            BrowserSessionPhase.NAVIGATING,
            BrowserSessionPhase.DOCUMENT_CREATED,
            BrowserSessionPhase.FAILED,
            BrowserSessionPhase.RENDERER_TERMINATED,
            BrowserSessionPhase.CLOSED,
        ),
        BrowserSessionPhase.FAILED to setOf(
            BrowserSessionPhase.NAVIGATING,
            BrowserSessionPhase.DOCUMENT_CREATED,
            BrowserSessionPhase.RENDERER_TERMINATED,
            BrowserSessionPhase.CLOSED,
        ),
        BrowserSessionPhase.RENDERER_TERMINATED to setOf(BrowserSessionPhase.CLOSED),
        BrowserSessionPhase.CLOSED to emptySet(),
    )
}

internal sealed interface LifecycleReduction {
    data class Accept(val state: BrowserSessionState) : LifecycleReduction
    data class Reject(val reason: String) : LifecycleReduction
}
