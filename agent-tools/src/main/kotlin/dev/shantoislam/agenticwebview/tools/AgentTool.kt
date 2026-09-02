package dev.shantoislam.agenticwebview.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AgentToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val outputSchema: JsonObject? = null,
) {
    init {
        require(name.matches(NAME_PATTERN)) { "Tool name has an invalid format: $name" }
        require(description.isNotBlank()) { "Tool description must not be blank" }
    }

    private companion object {
        val NAME_PATTERN = Regex("^[a-z][a-z0-9_]{0,63}$")
    }
}

interface AgentToolDispatcher {
    val definitions: List<AgentToolDefinition>
    suspend fun invoke(name: String, arguments: JsonObject): AgentToolInvocationResult
}

@Serializable
data class AgentToolInvocationResult(
    val success: Boolean,
    val output: JsonObject,
)
