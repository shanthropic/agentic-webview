package dev.shantoislam.agenticwebview.integrations.jsonrpc

import dev.shantoislam.agenticwebview.tools.AgentToolDefinition
import dev.shantoislam.agenticwebview.tools.AgentToolDispatcher
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Transport-neutral JSON-RPC 2.0 endpoint for browser tool discovery and invocation.
 * Hosts can place this behind HTTP, WebSocket, stdio, or an in-process message bus.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class JsonRpcBrowserToolServer(
    private val dispatcher: AgentToolDispatcher,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    },
) {
    suspend fun handle(requestJson: String): String? {
        val response = try {
            handleObject(json.parseToJsonElement(requestJson).jsonObject)
        } catch (error: SerializationException) {
            errorResponse(JsonNull, PARSE_ERROR, error.message ?: "Invalid JSON")
        } catch (error: IllegalArgumentException) {
            errorResponse(JsonNull, INVALID_REQUEST, error.message ?: "Invalid JSON-RPC request")
        }
        return response?.let { json.encodeToString(JsonObject.serializer(), it) }
    }

    suspend fun handleObject(request: JsonObject): JsonObject? {
        val notification = "id" !in request
        val id = request["id"] ?: JsonNull
        if (request["jsonrpc"]?.jsonPrimitive?.contentOrNull != "2.0") {
            return errorResponse(id, INVALID_REQUEST, "jsonrpc must equal '2.0'")
        }
        val method = request["method"]?.jsonPrimitive?.contentOrNull
            ?: return errorResponse(id, INVALID_REQUEST, "method must be a string")
        val response = when (method) {
            "tools/list" -> successResponse(
                id,
                buildJsonObject {
                    put(
                        "tools",
                        json.encodeToJsonElement(ListSerializer(AgentToolDefinition.serializer()), dispatcher.definitions),
                    )
                },
            )
            "tools/call" -> callTool(id, request["params"])
            else -> errorResponse(id, METHOD_NOT_FOUND, "Unknown method: $method")
        }
        return if (notification) null else response
    }

    private suspend fun callTool(id: JsonElement, paramsElement: JsonElement?): JsonObject {
        val params = paramsElement as? JsonObject
            ?: return errorResponse(id, INVALID_PARAMS, "params must be an object")
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: return errorResponse(id, INVALID_PARAMS, "params.name must be a string")
        val arguments = when (val candidate = params["arguments"]) {
            null -> buildJsonObject { }
            is JsonObject -> candidate
            else -> return errorResponse(id, INVALID_PARAMS, "params.arguments must be an object")
        }
        val invocation = dispatcher.invoke(name, arguments)
        return successResponse(
            id,
            buildJsonObject {
                put("success", invocation.success)
                put("output", invocation.output)
            },
        )
    }

    private fun successResponse(id: JsonElement, result: JsonElement): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun errorResponse(id: JsonElement, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
        })
    }

    private companion object {
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
    }
}
