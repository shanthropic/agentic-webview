package dev.shantoislam.agenticwebview.app

import dev.shantoislam.agenticwebview.app.agent.BrowserAgentTools
import dev.shantoislam.agenticwebview.tools.AgentToolDefinition
import dev.shantoislam.agenticwebview.tools.AgentToolDispatcher
import dev.shantoislam.agenticwebview.tools.AgentToolInvocationResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BrowserAgentToolsTest {

    private class CapturingDispatcher : AgentToolDispatcher {
        override val definitions: List<AgentToolDefinition> = emptyList()
        var lastName: String? = null
        var lastArguments: JsonObject? = null

        override suspend fun invoke(name: String, arguments: JsonObject): AgentToolInvocationResult {
            lastName = name
            lastArguments = arguments
            return AgentToolInvocationResult(
                success = true,
                output = buildJsonObject { put("status", "success") },
            )
        }
    }

    @Test
    fun translatesObserveArgumentsCorrectly() = runTest {
        val dispatcher = CapturingDispatcher()
        val tools = BrowserAgentTools(dispatcher)

        tools.browser_observe(
            includeScreenshot = true,
            includeCompactText = false,
            includeOffscreenContent = true,
        )

        assertEquals("browser_observe", dispatcher.lastName)
        val args = dispatcher.lastArguments!!
        assertEquals("true", args["include_screenshot"]?.jsonPrimitive?.content)
        assertEquals("false", args["include_compact_text"]?.jsonPrimitive?.content)
        assertEquals("true", args["include_offscreen_content"]?.jsonPrimitive?.content)
    }

    @Test
    fun translatesNavigateArgumentsCorrectly() = runTest {
        val dispatcher = CapturingDispatcher()
        val tools = BrowserAgentTools(dispatcher)

        tools.browser_navigate("https://kotlinlang.org")

        assertEquals("browser_navigate", dispatcher.lastName)
        val args = dispatcher.lastArguments!!
        assertEquals("https://kotlinlang.org", args["url"]?.jsonPrimitive?.content)
    }

    @Test
    fun translatesClickTargetArgumentsCorrectly() = runTest {
        val dispatcher = CapturingDispatcher()
        val tools = BrowserAgentTools(dispatcher)

        tools.browser_click(
            documentId = "doc-1",
            frameId = "frame-main",
            elementId = "elem-button",
            observedAtRevision = 42,
        )

        assertEquals("browser_click", dispatcher.lastName)
        val target = dispatcher.lastArguments?.get("target")?.jsonObject
        assertNotNull(target)
        assertEquals("doc-1", target!!["documentId"]?.jsonPrimitive?.content)
        assertEquals("frame-main", target["frameId"]?.jsonPrimitive?.content)
        assertEquals("elem-button", target["elementId"]?.jsonPrimitive?.content)
        assertEquals("42", target["observedAtRevision"]?.jsonPrimitive?.content)
    }

    @Test
    fun translatesTypeTextArgumentsCorrectly() = runTest {
        val dispatcher = CapturingDispatcher()
        val tools = BrowserAgentTools(dispatcher)

        tools.browser_type_text(
            documentId = "doc-1",
            frameId = "frame-main",
            elementId = "elem-input",
            observedAtRevision = 5,
            text = "Hello world",
            mode = "append",
        )

        assertEquals("browser_type_text", dispatcher.lastName)
        val args = dispatcher.lastArguments!!
        val target = args["target"]?.jsonObject
        assertNotNull(target)
        assertEquals("Hello world", args["text"]?.jsonPrimitive?.content)
        assertEquals("append", args["mode"]?.jsonPrimitive?.content)
    }

    @Test
    fun translatesSelectOptionWithMatcherTypes() = runTest {
        val dispatcher = CapturingDispatcher()
        val tools = BrowserAgentTools(dispatcher)

        tools.browser_select_option("d", "f", "e", 1, "value", "option-v")
        assertEquals("option-v", dispatcher.lastArguments!!["value"]?.jsonPrimitive?.content)

        tools.browser_select_option("d", "f", "e", 1, "label", "Option Label")
        assertEquals("Option Label", dispatcher.lastArguments!!["label"]?.jsonPrimitive?.content)

        tools.browser_select_option("d", "f", "e", 1, "index", "2")
        assertEquals("2", dispatcher.lastArguments!!["index"]?.jsonPrimitive?.content)
    }

    @Test
    fun translatesScrollArgumentsCorrectly() = runTest {
        val dispatcher = CapturingDispatcher()
        val tools = BrowserAgentTools(dispatcher)

        tools.browser_scroll(deltaYCssPx = 300.0, deltaXCssPx = 50.0)

        assertEquals("browser_scroll", dispatcher.lastName)
        val args = dispatcher.lastArguments!!
        assertEquals("300.0", args["delta_y_css_px"]?.jsonPrimitive?.content)
        assertEquals("50.0", args["delta_x_css_px"]?.jsonPrimitive?.content)
    }

    @Test
    fun translatesPressKeysArgumentsCorrectly() = runTest {
        val dispatcher = CapturingDispatcher()
        val tools = BrowserAgentTools(dispatcher)

        tools.browser_press_keys(
            key = "Enter",
            control = true,
            alt = false,
            shift = true,
            meta = false,
        )

        assertEquals("browser_press_keys", dispatcher.lastName)
        val args = dispatcher.lastArguments!!
        assertEquals("Enter", args["key"]?.jsonPrimitive?.content)
        assertEquals("true", args["control"]?.jsonPrimitive?.content)
        assertEquals("false", args["alt"]?.jsonPrimitive?.content)
        assertEquals("true", args["shift"]?.jsonPrimitive?.content)
        assertEquals("false", args["meta"]?.jsonPrimitive?.content)
    }

    @Test
    fun translatesNavigationHistoryTools() = runTest {
        val dispatcher = CapturingDispatcher()
        val tools = BrowserAgentTools(dispatcher)

        tools.browser_go_back()
        assertEquals("browser_go_back", dispatcher.lastName)

        tools.browser_go_forward()
        assertEquals("browser_go_forward", dispatcher.lastName)

        tools.browser_reload()
        assertEquals("browser_reload", dispatcher.lastName)
    }
}
