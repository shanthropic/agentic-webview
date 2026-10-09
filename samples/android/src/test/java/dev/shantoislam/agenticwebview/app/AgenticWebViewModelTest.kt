package dev.shantoislam.agenticwebview.app

import dev.shantoislam.agenticwebview.app.data.AgentSettingsDao
import dev.shantoislam.agenticwebview.app.data.AgentSettingsEntity
import dev.shantoislam.agenticwebview.app.ui.AgenticWebViewModel
import dev.shantoislam.agenticwebview.app.ui.ChatItem
import dev.shantoislam.agenticwebview.app.ui.components.ToolStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgenticWebViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeAgentSettingsDao : AgentSettingsDao {
        val flow = MutableStateFlow<AgentSettingsEntity?>(AgentSettingsEntity())
        override fun getSettings(): Flow<AgentSettingsEntity?> = flow
        override suspend fun getSettingsOnce(): AgentSettingsEntity? = flow.value
        override suspend fun saveSettings(settings: AgentSettingsEntity) {
            flow.value = settings
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateHasGreetingAndDefaultSettings() = runTest {
        val dao = FakeAgentSettingsDao()
        val viewModel = AgenticWebViewModel(dao)

        assertEquals("", viewModel.draft.value)
        assertEquals(false, viewModel.isWorking.value)
        assertEquals(false, viewModel.isThinking.value)
        assertEquals(1, viewModel.messages.value.size)
        assertTrue(viewModel.messages.value[0] is ChatItem.Assistant)
    }

    @Test
    fun onDraftChangeUpdatesDraftState() = runTest {
        val dao = FakeAgentSettingsDao()
        val viewModel = AgenticWebViewModel(dao)

        viewModel.onDraftChange("Search for Kotlin documentation")
        assertEquals("Search for Kotlin documentation", viewModel.draft.value)
    }

    @Test
    fun toolCallLifecycleTransitionsCorrectly() = runTest {
        val dao = FakeAgentSettingsDao()
        val viewModel = AgenticWebViewModel(dao)

        viewModel.handleToolStarted("call-1", "browser_navigate", "{\"url\":\"https://example.com\"}")
        val runningItem = viewModel.messages.value.last() as ChatItem.ToolCall
        assertEquals("call-1", runningItem.id)
        assertEquals("browser_navigate", runningItem.toolName)
        assertEquals(ToolStatus.RUNNING, runningItem.status)

        viewModel.handleToolCompleted("call-1", "browser_navigate", "{\"status\":\"success\"}")
        val completedItem = viewModel.messages.value.last() as ChatItem.ToolCall
        assertEquals(ToolStatus.SUCCESS, completedItem.status)
    }

    @Test
    fun toolCallWithStatusErrorBecomesFailure() = runTest {
        val dao = FakeAgentSettingsDao()
        val viewModel = AgenticWebViewModel(dao)

        viewModel.handleToolStarted("call-2", "browser_click", "{\"target\":{}}")
        viewModel.handleToolCompleted("call-2", "browser_click", "{\"status\":\"error\",\"error\":\"ELEMENT_NOT_FOUND\"}")

        val failedItem = viewModel.messages.value.last() as ChatItem.ToolCall
        assertEquals(ToolStatus.FAILURE, failedItem.status)
    }

    @Test
    fun stopAgentMarksRunningToolsAsCancelled() = runTest {
        val dao = FakeAgentSettingsDao()
        val viewModel = AgenticWebViewModel(dao)

        viewModel.handleToolStarted("call-3", "browser_observe", "{}")
        assertEquals(ToolStatus.RUNNING, (viewModel.messages.value.last() as ChatItem.ToolCall).status)

        viewModel.stopAgent()
        val itemAfterStop = viewModel.messages.value.first { it is ChatItem.ToolCall && it.id == "call-3" } as ChatItem.ToolCall
        assertEquals(ToolStatus.FAILURE, itemAfterStop.status)
        assertEquals("Execution cancelled", itemAfterStop.resultJson)
        assertEquals(false, viewModel.isWorking.value)
        assertEquals(false, viewModel.isThinking.value)
    }
}
