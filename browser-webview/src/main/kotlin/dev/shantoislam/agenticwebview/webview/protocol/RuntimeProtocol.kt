package dev.shantoislam.agenticwebview.webview.protocol

import dev.shantoislam.agenticwebview.api.DocumentId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

internal const val RUNTIME_PROTOCOL_VERSION: Int = 1

@Serializable
internal data class RuntimeRequestEnvelope(
    val protocolVersion: Int = RUNTIME_PROTOCOL_VERSION,
    val bridgeToken: String,
    val sessionId: String,
    val documentId: DocumentId,
    val requestId: String,
    val method: String,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(bridgeToken.length >= 32) { "bridgeToken must contain at least 32 characters" }
        require(requestId.isNotBlank()) { "requestId must not be blank" }
        require(method.matches(METHOD_PATTERN)) { "method has an invalid format" }
    }

    companion object {
        val METHOD_PATTERN = Regex("^[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9_]*)+$")
    }
}

@Serializable
internal data class RuntimeResponseEnvelope(
    val protocolVersion: Int,
    val bridgeToken: String,
    val sessionId: String,
    val documentId: DocumentId,
    val requestId: String,
    val status: RuntimeResponseStatus,
    val result: JsonElement = JsonNull,
    val error: RuntimeProtocolError? = null,
) {
    fun validationError(expected: RuntimeRequestEnvelope): String? = when {
        protocolVersion != RUNTIME_PROTOCOL_VERSION ->
            "Protocol version mismatch: expected $RUNTIME_PROTOCOL_VERSION but received $protocolVersion"
        bridgeToken != expected.bridgeToken -> "Response bridgeToken does not match request"
        sessionId != expected.sessionId -> "Response sessionId does not match request"
        documentId != expected.documentId -> "Response documentId does not match request"
        requestId != expected.requestId -> "Response requestId does not match request"
        status == RuntimeResponseStatus.SUCCESS && error != null -> "Successful response must not contain an error"
        status == RuntimeResponseStatus.ERROR && error == null -> "Error response must contain an error"
        else -> null
    }
}

@Serializable
internal enum class RuntimeResponseStatus {
    @kotlinx.serialization.SerialName("success")
    SUCCESS,
    @kotlinx.serialization.SerialName("error")
    ERROR,
}

@Serializable
internal data class RuntimeProtocolError(
    val code: String,
    val message: String,
    val details: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(code.matches(CODE_PATTERN)) { "Runtime error code has an invalid format" }
        require(message.isNotBlank()) { "Runtime error message must not be blank" }
    }

    companion object {
        val CODE_PATTERN = Regex("^[A-Z][A-Z0-9_]*$")
    }
}

internal object RuntimeMethods {
    const val PING = "system.ping"
    const val CONFIGURE = "runtime.configure"
    const val CAPTURE_OBSERVATION = "observation.capture"
    const val EXECUTE_ACTION = "action.execute"
    const val PREPARE_NATIVE_CLICK = "action.prepare_native_click"
    const val VERIFY_NATIVE_CLICK = "action.verify_native_click"
}
