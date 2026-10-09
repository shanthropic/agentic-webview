package dev.shantoislam.agenticwebview.app

import dev.shantoislam.agenticwebview.api.AgenticBrowserSession
import dev.shantoislam.agenticwebview.api.BrowserCommand
import dev.shantoislam.agenticwebview.api.BrowserEvent
import dev.shantoislam.agenticwebview.api.BrowserObservation
import dev.shantoislam.agenticwebview.api.BrowserResult
import dev.shantoislam.agenticwebview.api.BrowserSessionPhase
import dev.shantoislam.agenticwebview.api.BrowserSessionState
import dev.shantoislam.agenticwebview.api.CommandReceipt
import dev.shantoislam.agenticwebview.api.HistoryNavigationRequest
import dev.shantoislam.agenticwebview.api.NavigationReceipt
import dev.shantoislam.agenticwebview.api.NavigationRequest
import dev.shantoislam.agenticwebview.api.ObservationOptions
import dev.shantoislam.agenticwebview.api.ObservationRevision
import dev.shantoislam.agenticwebview.api.WaitCondition
import dev.shantoislam.agenticwebview.api.WaitReceipt
import dev.shantoislam.agenticwebview.app.agent.SimulationRunner
import dev.shantoislam.agenticwebview.tools.AgentToolDefinition
import dev.shantoislam.agenticwebview.tools.AgentToolDispatcher
import dev.shantoislam.agenticwebview.tools.AgentToolInvocationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationRunnerTest {

    private class FakeSession : AgenticBrowserSession {
        val stateFlow = MutableStateFlow(
            BrowserSessionState(
                phase = BrowserSessionPhase.READY,
                url = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
                revision = ObservationRevision(3),
            )
        )
        override val state: StateFlow<BrowserSessionState> = stateFlow
        override val events: Flow<BrowserEvent> = emptyFlow()

        override suspend fun navigate(request: NavigationRequest): BrowserResult<NavigationReceipt> = error("not implemented")
        override suspend fun navigateHistory(request: HistoryNavigationRequest): BrowserResult<NavigationReceipt> = error("not implemented")
        override suspend fun observe(options: ObservationOptions): BrowserResult<BrowserObservation> = error("not implemented")
        override suspend fun execute(command: BrowserCommand): BrowserResult<CommandReceipt> = error("not implemented")
        override suspend fun await(condition: WaitCondition): BrowserResult<WaitReceipt> = error("not implemented")
        override fun close() {}
    }

    private class RecordingDispatcher : AgentToolDispatcher {
        override val definitions: List<AgentToolDefinition> = emptyList()
        val invokedTools = mutableListOf<String>()

        override suspend fun invoke(name: String, arguments: JsonObject): AgentToolInvocationResult {
            invokedTools.add(name)
            return AgentToolInvocationResult(
                success = true,
                output = buildJsonObject { put("status", "success") },
            )
        }
    }

    @Test
    fun runsScrollBranchWhenPromptContainsScroll() = runTest {
        val session = FakeSession()
        val dispatcher = RecordingDispatcher()
        val runner = SimulationRunner(session, dispatcher)

        val thoughts = mutableListOf<String>()
        val toolsStarted = mutableListOf<String>()

        val result = runner.runSimulation(
            userPrompt = "Please scroll down the page",
            onToolStarted = { _, name, _ -> toolsStarted.add(name) },
            onToolCompleted = { _, _, _ -> },
            onToolFailed = { _, _, _ -> },
            onThinkingChanged = { _ -> },
            onThoughtUpdate = { thoughts.add(it) },
        )

        assertEquals(listOf("browser_scroll"), dispatcher.invokedTools)
        assertEquals(listOf("browser_scroll"), toolsStarted)
        assertTrue(result.contains("Scrolled the webpage down"))
        assertTrue(thoughts.any { it.contains("scroll", ignoreCase = true) })
    }

    @Test
    fun runsNavigationAndObservationWhenPromptRequestsNavigation() = runTest {
        val session = FakeSession()
        val dispatcher = RecordingDispatcher()
        val runner = SimulationRunner(session, dispatcher)

        val thoughts = mutableListOf<String>()
        val toolsStarted = mutableListOf<String>()

        val result = runner.runSimulation(
            userPrompt = "Navigate to DuckDuckGo",
            onToolStarted = { _, name, _ -> toolsStarted.add(name) },
            onToolCompleted = { _, _, _ -> },
            onToolFailed = { _, _, _ -> },
            onThinkingChanged = { _ -> },
            onThoughtUpdate = { thoughts.add(it) },
        )

        assertEquals(listOf("browser_navigate", "browser_observe"), dispatcher.invokedTools)
        assertEquals(listOf("browser_navigate", "browser_observe"), toolsStarted)
        assertTrue(result.contains("Successfully navigated"))
        assertTrue(thoughts.any { it.contains("duckduckgo", ignoreCase = true) })
    }

    @Test
    fun runsObserveBranchWhenPromptRequestsInspectOnly() = runTest {
        val session = FakeSession()
        val dispatcher = RecordingDispatcher()
        val runner = SimulationRunner(session, dispatcher)

        val result = runner.runSimulation(
            userPrompt = "Inspect page DOM structure",
            onToolStarted = { _, _, _ -> },
            onToolCompleted = { _, _, _ -> },
            onToolFailed = { _, _, _ -> },
            onThinkingChanged = { _ -> },
            onThoughtUpdate = { _ -> },
        )

        assertEquals(listOf("browser_observe"), dispatcher.invokedTools)
        assertTrue(result.contains("Inspected current webpage"))
    }
}
