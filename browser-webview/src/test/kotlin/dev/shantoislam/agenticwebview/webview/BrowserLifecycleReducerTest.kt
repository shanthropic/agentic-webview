package dev.shantoislam.agenticwebview.webview

import dev.shantoislam.agenticwebview.api.BrowserSessionPhase
import dev.shantoislam.agenticwebview.api.BrowserSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserLifecycleReducerTest {
    @Test
    fun acceptsNormalInitializationSequence() {
        val phases = listOf(
            BrowserSessionPhase.ATTACHED,
            BrowserSessionPhase.NAVIGATING,
            BrowserSessionPhase.DOCUMENT_CREATED,
            BrowserSessionPhase.RUNTIME_INITIALIZING,
            BrowserSessionPhase.INTERACTIVE,
            BrowserSessionPhase.STABILIZING,
            BrowserSessionPhase.READY,
        )
        var state = BrowserSessionState(BrowserSessionPhase.DETACHED)

        phases.forEach { phase ->
            val reduction = BrowserLifecycleReducer.reduce(state, state.copy(phase = phase))
            assertTrue(reduction is LifecycleReduction.Accept)
            state = (reduction as LifecycleReduction.Accept).state
        }

        assertEquals(BrowserSessionPhase.READY, state.phase)
    }

    @Test
    fun closedSessionRejectsLateCallbacks() {
        val closed = BrowserSessionState(BrowserSessionPhase.CLOSED)

        val reduction = BrowserLifecycleReducer.reduce(
            closed,
            BrowserSessionState(BrowserSessionPhase.DOCUMENT_CREATED),
        )

        assertTrue(reduction is LifecycleReduction.Reject)
    }

    @Test
    fun rendererTerminationIsTerminalUntilClose() {
        val terminated = BrowserSessionState(BrowserSessionPhase.RENDERER_TERMINATED)

        assertTrue(
            BrowserLifecycleReducer.reduce(
                terminated,
                BrowserSessionState(BrowserSessionPhase.READY),
            ) is LifecycleReduction.Reject,
        )
        assertTrue(
            BrowserLifecycleReducer.reduce(
                terminated,
                BrowserSessionState(BrowserSessionPhase.CLOSED),
            ) is LifecycleReduction.Accept,
        )
    }
}
