package dev.shantoislam.agenticwebview.webview.protocol

import dev.shantoislam.agenticwebview.api.DocumentId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeProtocolCodecTest {
    private val codec = RuntimeProtocolCodec(maximumMessageBytes = 8_192)

    @Test
    fun requestRoundTripProducesExpectedEnvelope() {
        val request = request(payloadValue = "hello")
        val encoded = codec.encodeRequest(request)

        assertTrue(encoded is CodecResult.Success)
        val value = (encoded as CodecResult.Success).value
        assertTrue(value.contains("\"protocolVersion\":1"))
        assertTrue(value.contains("\"method\":\"system.ping\""))
    }

    @Test
    fun malformedResponseIsRejected() {
        val result = codec.decodeResponse("{not-json")

        assertTrue(result is CodecResult.Failure)
    }

    @Test
    fun oversizedResponseIsRejectedBeforeParsing() {
        val result = RuntimeProtocolCodec(maximumMessageBytes = 16).decodeResponse("x".repeat(17))

        assertTrue(result is CodecResult.Failure)
    }

    @Test
    fun responseValidationRejectsWrongDocument() {
        val request = request()
        val response = successResponse(request).copy(documentId = DocumentId("other-document"))

        assertEquals("Response documentId does not match request", response.validationError(request))
    }

    @Test
    fun responseValidationRejectsSpoofedBridgeToken() {
        val request = request()
        val response = successResponse(request).copy(bridgeToken = "different-token-123456789012345678901234567890")

        assertEquals("Response bridgeToken does not match request", response.validationError(request))
    }

    @Test
    fun sharedGoldenFixturesDecodeWithTheKotlinProtocol() {
        val success = fixture("v1/response-ping-success.json")
        val failure = fixture("v1/response-runtime-error.json")

        val successResult = codec.decodeResponse(success)
        val failureResult = codec.decodeResponse(failure)

        assertTrue(successResult is CodecResult.Success)
        assertTrue(failureResult is CodecResult.Success)
        assertEquals(RuntimeResponseStatus.SUCCESS, (successResult as CodecResult.Success).value.status)
        assertEquals("NOT_READY", (failureResult as CodecResult.Success).value.error?.code)
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "Missing protocol fixture: $name" }.readText()

    private fun request(payloadValue: String = "value") = RuntimeRequestEnvelope(
        bridgeToken = TEST_BRIDGE_TOKEN,
        sessionId = "session-1",
        documentId = DocumentId("document-1"),
        requestId = "request-1",
        method = RuntimeMethods.PING,
        payload = buildJsonObject { put("value", payloadValue) },
    )

    private fun successResponse(request: RuntimeRequestEnvelope) = RuntimeResponseEnvelope(
        protocolVersion = RUNTIME_PROTOCOL_VERSION,
        bridgeToken = request.bridgeToken,
        sessionId = request.sessionId,
        documentId = request.documentId,
        requestId = request.requestId,
        status = RuntimeResponseStatus.SUCCESS,
        result = buildJsonObject { put("ok", true) },
    )

    private companion object {
        const val TEST_BRIDGE_TOKEN = "bridge-token-12345678901234567890123456789012"
    }
}
