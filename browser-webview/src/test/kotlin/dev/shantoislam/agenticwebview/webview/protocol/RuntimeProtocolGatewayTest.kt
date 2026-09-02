package dev.shantoislam.agenticwebview.webview.protocol

import dev.shantoislam.agenticwebview.api.DocumentId
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeProtocolGatewayTest {
    private val json = Json { explicitNulls = false; encodeDefaults = true }

    @Test
    fun transportReceivesRegisteredRequestAndResponseCompletesIt() = runTest {
        lateinit var gateway: RuntimeProtocolGateway
        val transport = RuntimeTransport { rawRequest ->
            val request = json.decodeFromString<RuntimeRequestEnvelope>(rawRequest)
            val response = RuntimeResponseEnvelope(
                protocolVersion = RUNTIME_PROTOCOL_VERSION,
                bridgeToken = request.bridgeToken,
                sessionId = request.sessionId,
                documentId = request.documentId,
                requestId = request.requestId,
                status = RuntimeResponseStatus.SUCCESS,
                result = JsonNull,
            )
            assertEquals(IncomingResponseResult.Completed, gateway.acceptResponse(json.encodeToString(response)))
        }
        gateway = gateway(transport)

        val result = gateway.request("session-1", DocumentId("document-1"), RuntimeMethods.PING)

        assertTrue(result is GatewayResult.Response)
    }

    @Test
    fun malformedIncomingResponseDoesNotCompletePendingRequest() = runTest {
        var capturedRequest: RuntimeRequestEnvelope? = null
        val gateway = gateway(RuntimeTransport { raw ->
            capturedRequest = json.decodeFromString(raw)
        })
        val result = async {
            gateway.request("session-1", DocumentId("document-1"), RuntimeMethods.PING)
        }
        while (capturedRequest == null) yield()

        val incoming = gateway.acceptResponse("{invalid")
        assertTrue(incoming is IncomingResponseResult.Rejected)

        gateway.cancelDocument(DocumentId("document-1"), "test complete")
        assertEquals(GatewayResult.Cancelled("test complete"), result.await())
    }

    @Test
    fun closedGatewayRejectsNewRequests() = runTest {
        val gateway = gateway(RuntimeTransport { })
        gateway.close()

        val result = gateway.request("session-1", DocumentId("document-1"), RuntimeMethods.PING)

        assertEquals(GatewayResult.Closed, result)
    }

    private fun gateway(transport: RuntimeTransport) = RuntimeProtocolGateway(
        bridgeToken = "bridge-token-12345678901234567890123456789012",
        maximumPendingRequests = 4,
        maximumMessageBytes = 8_192,
        requestTimeoutMs = 1_000,
        transport = transport,
        requestIdFactory = { "request-1" },
    )
}
