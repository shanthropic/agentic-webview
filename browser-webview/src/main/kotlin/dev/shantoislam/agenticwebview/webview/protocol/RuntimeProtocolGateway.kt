package dev.shantoislam.agenticwebview.webview.protocol

import dev.shantoislam.agenticwebview.api.DocumentId
import java.util.UUID
import kotlinx.serialization.json.JsonObject

internal class RuntimeProtocolGateway(
    private val bridgeToken: String,
    maximumPendingRequests: Int,
    maximumMessageBytes: Int,
    private val requestTimeoutMs: Long,
    private val transport: RuntimeTransport,
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val codec = RuntimeProtocolCodec(maximumMessageBytes)
    private val registry = RuntimeRequestRegistry(maximumPendingRequests)

    init { require(requestTimeoutMs > 0) { "requestTimeoutMs must be positive" } }

    suspend fun request(
        sessionId: String,
        documentId: DocumentId,
        method: String,
        payload: JsonObject = JsonObject(emptyMap()),
        timeoutMs: Long = requestTimeoutMs,
    ): GatewayResult {
        if (timeoutMs <= 0) return GatewayResult.RequestRejected("timeoutMs must be positive")
        val envelope = try {
            RuntimeRequestEnvelope(
                bridgeToken = bridgeToken,
                sessionId = sessionId,
                documentId = documentId,
                requestId = requestIdFactory(),
                method = method,
                payload = payload,
            )
        } catch (error: IllegalArgumentException) {
            return GatewayResult.RequestRejected(error.message ?: "Invalid runtime request")
        }

        return when (val result = registry.dispatchAndAwait(envelope, timeoutMs) { request ->
            when (val encoded = codec.encodeRequest(request)) {
                is CodecResult.Success -> transport.dispatch(encoded.value)
                is CodecResult.Failure -> throw RuntimeDispatchException(encoded.reason)
            }
        }) {
            is RegistryResult.Response -> GatewayResult.Response(result.value)
            is RegistryResult.Rejected -> GatewayResult.ResponseRejected(result.reason)
            is RegistryResult.LimitExceeded -> GatewayResult.PendingLimitExceeded(result.limit)
            is RegistryResult.TimedOut -> GatewayResult.TimedOut(result.timeoutMs)
            is RegistryResult.Cancelled -> GatewayResult.Cancelled(result.reason)
            is RegistryResult.DispatchFailed -> GatewayResult.DispatchFailed(result.reason)
            RegistryResult.Closed -> GatewayResult.Closed
        }
    }

    suspend fun acceptResponse(raw: String): IncomingResponseResult = when (val decoded = codec.decodeResponse(raw)) {
        is CodecResult.Failure -> IncomingResponseResult.Rejected(decoded.reason)
        is CodecResult.Success -> when (registry.complete(decoded.value)) {
            CompletionResult.Completed -> IncomingResponseResult.Completed
            CompletionResult.AlreadyCompleted -> IncomingResponseResult.AlreadyCompleted
            CompletionResult.UnknownRequest -> IncomingResponseResult.UnknownRequest
        }
    }

    suspend fun cancelDocument(documentId: DocumentId, reason: String): Int =
        registry.cancelDocument(documentId, reason)

    suspend fun close(reason: String = "Runtime gateway closed"): Int = registry.close(reason)
}

internal fun interface RuntimeTransport {
    suspend fun dispatch(encodedRequest: String)
}

internal sealed interface GatewayResult {
    data class Response(val envelope: RuntimeResponseEnvelope) : GatewayResult
    data class RequestRejected(val reason: String) : GatewayResult
    data class ResponseRejected(val reason: String) : GatewayResult
    data class PendingLimitExceeded(val limit: Int) : GatewayResult
    data class TimedOut(val timeoutMs: Long) : GatewayResult
    data class Cancelled(val reason: String) : GatewayResult
    data class DispatchFailed(val reason: String) : GatewayResult
    data object Closed : GatewayResult
}

internal sealed interface IncomingResponseResult {
    data object Completed : IncomingResponseResult
    data object AlreadyCompleted : IncomingResponseResult
    data object UnknownRequest : IncomingResponseResult
    data class Rejected(val reason: String) : IncomingResponseResult
}

private class RuntimeDispatchException(message: String) : RuntimeException(message)
