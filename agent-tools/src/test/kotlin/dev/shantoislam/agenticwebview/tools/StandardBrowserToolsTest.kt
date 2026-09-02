package dev.shantoislam.agenticwebview.tools

import dev.shantoislam.agenticwebview.api.AgenticBrowserSession
import dev.shantoislam.agenticwebview.api.BrowserCommand
import dev.shantoislam.agenticwebview.api.BrowserError
import dev.shantoislam.agenticwebview.api.BrowserEvent
import dev.shantoislam.agenticwebview.api.BrowserObservation
import dev.shantoislam.agenticwebview.api.BrowserResult
import dev.shantoislam.agenticwebview.api.BrowserScreenshot
import dev.shantoislam.agenticwebview.api.BrowserSessionPhase
import dev.shantoislam.agenticwebview.api.BrowserSessionState
import dev.shantoislam.agenticwebview.api.BrowserViewport
import dev.shantoislam.agenticwebview.api.CommandReceipt
import dev.shantoislam.agenticwebview.api.DocumentId
import dev.shantoislam.agenticwebview.api.ActionExecutionStrategy
import dev.shantoislam.agenticwebview.api.HistoryNavigationRequest
import dev.shantoislam.agenticwebview.api.NavigationOperation
import dev.shantoislam.agenticwebview.api.NavigationReceipt
import dev.shantoislam.agenticwebview.api.NavigationRequest
import dev.shantoislam.agenticwebview.api.ObservationOptions
import dev.shantoislam.agenticwebview.api.ObservationId
import dev.shantoislam.agenticwebview.api.ObservationRevision
import dev.shantoislam.agenticwebview.api.PageDocument
import dev.shantoislam.agenticwebview.api.WaitCondition
import dev.shantoislam.agenticwebview.api.WaitReceipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardBrowserToolsTest {
    @Test
    fun exposesFrameworkNeutralToolDefinitions() {
        val tools = StandardBrowserTools(FakeSession())

        assertEquals(
            listOf(
                "browser_observe",
                "browser_navigate",
                "browser_click",
                "browser_type_text",
                "browser_select_option",
                "browser_scroll",
                "browser_press_keys",
                "browser_go_back",
                "browser_go_forward",
                "browser_reload",
            ),
            tools.definitions.map { it.name },
        )
        assertTrue(tools.definitions.all { it.inputSchema["type"] == JsonPrimitive("object") })
        assertTrue(tools.definitions.all { it.outputSchema != null })
    }

    @Test
    fun toolProfilesOnlyExposeTheirDeclaredSurface() {
        assertEquals(4, StandardBrowserTools(FakeSession(), BrowserToolProfile.MINIMAL).definitions.size)
        assertEquals(10, StandardBrowserTools(FakeSession(), BrowserToolProfile.STANDARD).definitions.size)
        assertEquals(12, StandardBrowserTools(FakeSession(), BrowserToolProfile.ADVANCED).definitions.size)
    }

    @Test
    fun navigateDispatchesTypedRequest() = runTest {
        val session = FakeSession()
        val tools = StandardBrowserTools(session)

        val result = tools.invoke("browser_navigate", buildJsonObject { put("url", "https://example.com") })

        assertTrue(result.success)
        assertEquals("https://example.com", session.lastNavigation?.url)
        assertEquals("success", result.output["status"]?.jsonPrimitive?.content)
        assertEquals("true", result.output["recommendObservation"]?.jsonPrimitive?.content)
    }

    @Test
    fun historyToolDispatchesTypedOperation() = runTest {
        val session = FakeSession()
        val result = StandardBrowserTools(session).invoke("browser_go_back", buildJsonObject { })

        assertTrue(result.success)
        assertEquals(NavigationOperation.BACK, session.lastHistoryNavigation?.operation)
    }

    @Test
    fun invalidArgumentsReturnMachineReadableFailure() = runTest {
        val tools = StandardBrowserTools(FakeSession())

        val result = tools.invoke("browser_navigate", buildJsonObject {})

        assertFalse(result.success)
        assertEquals("error", result.output["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun browserFailureRemainsStructured() = runTest {
        val session = FakeSession().apply {
            navigationResult = BrowserResult.Failure(BrowserError.NavigationBlocked("https://blocked.test", "test policy"))
        }
        val tools = StandardBrowserTools(session)

        val result = tools.invoke("browser_navigate", buildJsonObject { put("url", "https://blocked.test") })

        assertFalse(result.success)
        assertEquals("error", result.output["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun screenshotBytesAreBase64EncodedAtTheToolBoundary() = runTest {
        val session = FakeSession().apply {
            observationResult = BrowserResult.Success(
                BrowserObservation(
                    id = ObservationId("observation-1"),
                    capturedAtEpochMs = 1,
                    document = PageDocument(
                        id = DocumentId("document-1"),
                        url = "https://example.com",
                        title = "Example",
                        phase = BrowserSessionPhase.READY,
                    ),
                    revision = ObservationRevision(0),
                    viewport = BrowserViewport(0.0, 0.0, 100.0, 100.0, 100.0, 100.0, 1.0, 1.0),
                    frames = emptyList(),
                    nodes = emptyList(),
                    compactText = "",
                    screenshot = BrowserScreenshot(byteArrayOf(1, 2, 3), "image/jpeg", 1, 1),
                ),
            )
        }

        val result = StandardBrowserTools(session).invoke(
            "browser_observe",
            buildJsonObject { put("include_screenshot", true) },
        )

        val screenshot = result.output["value"]!!.jsonObject["screenshot"]!!.jsonObject
        assertEquals("AQID", screenshot["dataBase64"]?.jsonPrimitive?.content)
        assertTrue("bytes" !in screenshot)
    }

    private class FakeSession : AgenticBrowserSession {
        override val state = MutableStateFlow(BrowserSessionState(BrowserSessionPhase.READY, DocumentId("document-1")))
        override val events: Flow<BrowserEvent> = emptyFlow()
        var lastNavigation: NavigationRequest? = null
        var lastHistoryNavigation: HistoryNavigationRequest? = null
        var lastCommand: BrowserCommand? = null
        var navigationResult: BrowserResult<NavigationReceipt> = BrowserResult.Success(
            NavigationReceipt(
                operation = NavigationOperation.URL,
                requestedUrl = "https://example.com",
                finalUrl = "https://example.com",
                documentId = DocumentId("document-1"),
                phase = BrowserSessionPhase.READY,
            ),
        )
        var observationResult: BrowserResult<BrowserObservation> =
            BrowserResult.Failure(BrowserError.RuntimeUnavailable())

        override suspend fun navigate(request: NavigationRequest): BrowserResult<NavigationReceipt> {
            lastNavigation = request
            return navigationResult
        }

        override suspend fun navigateHistory(request: HistoryNavigationRequest): BrowserResult<NavigationReceipt> {
            lastHistoryNavigation = request
            return BrowserResult.Success(
                NavigationReceipt(
                    operation = request.operation,
                    finalUrl = "https://example.com",
                    documentId = DocumentId("document-1"),
                    phase = BrowserSessionPhase.READY,
                ),
            )
        }

        override suspend fun observe(options: ObservationOptions): BrowserResult<BrowserObservation> =
            observationResult

        override suspend fun execute(command: BrowserCommand): BrowserResult<CommandReceipt> {
            lastCommand = command
            return BrowserResult.Success(
                CommandReceipt(
                    commandType = command::class.simpleName ?: "command",
                    strategy = ActionExecutionStrategy.DOM_POINTER,
                    documentId = DocumentId("document-1"),
                    revisionBefore = ObservationRevision(0),
                    revisionAfter = ObservationRevision(1),
                    dispatched = true,
                    verified = true,
                    pageChanged = true,
                ),
            )
        }

        override suspend fun await(condition: WaitCondition): BrowserResult<WaitReceipt> =
            BrowserResult.Failure(BrowserError.RuntimeUnavailable())

        override fun close() = Unit
    }
}
