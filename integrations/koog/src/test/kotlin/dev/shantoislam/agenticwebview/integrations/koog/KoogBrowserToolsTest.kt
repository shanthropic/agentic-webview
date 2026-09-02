package dev.shantoislam.agenticwebview.integrations.koog

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
import org.junit.Test

class KoogBrowserToolsTest {
    @Test
    fun translatesTypedElementFieldsIntoTheGenericTargetContract() = runTest {
        val dispatcher = CapturingDispatcher()
        val tools = KoogBrowserTools(dispatcher)

        tools.browser_click(
            documentId = "document-1",
            frameId = "main:f1",
            elementId = "main:f1:e2",
            observedAtRevision = 7,
        )

        assertEquals("browser_click", dispatcher.lastName)
        val target = dispatcher.lastArguments?.get("target")!!.jsonObject
        assertEquals("document-1", target["documentId"]?.jsonPrimitive?.content)
        assertEquals("main:f1", target["frameId"]?.jsonPrimitive?.content)
        assertEquals("main:f1:e2", target["elementId"]?.jsonPrimitive?.content)
        assertEquals("7", target["observedAtRevision"]?.jsonPrimitive?.content)
    }

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
}
