package dev.shantoislam.agenticwebview.integrations.jsonrpc

import dev.shantoislam.agenticwebview.tools.AgentToolDefinition
import dev.shantoislam.agenticwebview.tools.AgentToolDispatcher
import dev.shantoislam.agenticwebview.tools.AgentToolInvocationResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonRpcBrowserToolServerTest {
    private val dispatcher = object : AgentToolDispatcher {
        var invocationCount = 0
        override val definitions = listOf(
            AgentToolDefinition(
                name = "browser_test",
                description = "Test tool",
                inputSchema = buildJsonObject { put("type", "object") },
            ),
        )

        override suspend fun invoke(name: String, arguments: JsonObject): AgentToolInvocationResult {
            invocationCount++
            return AgentToolInvocationResult(true, buildJsonObject {
                put("status", "success")
                put("name", name)
            })
        }
    }

    @Test
    fun listsGenericToolDefinitions() = runTest {
        val response = requireNotNull(JsonRpcBrowserToolServer(dispatcher).handleObject(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/list")
            },
        ))

        val tools = response["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals("browser_test", tools.single().jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun invokesTheSameFrameworkNeutralDispatcher() = runTest {
        val response = requireNotNull(JsonRpcBrowserToolServer(dispatcher).handleObject(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", "call-1")
                put("method", "tools/call")
                put("params", buildJsonObject {
                    put("name", "browser_test")
                    put("arguments", buildJsonObject { })
                })
            },
        ))

        val result = response["result"]!!.jsonObject
        assertTrue(result["success"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("browser_test", result["output"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun executesNotificationsWithoutReturningAResponse() = runTest {
        val response = JsonRpcBrowserToolServer(dispatcher).handleObject(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "tools/call")
                put("params", buildJsonObject {
                    put("name", "browser_test")
                    put("arguments", buildJsonObject { })
                })
            },
        )

        assertEquals(null, response)
        assertEquals(1, dispatcher.invocationCount)
    }
}
