package dev.shantoislam.agenticwebview.webview.protocol

import dev.shantoislam.agenticwebview.api.DocumentId
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeRequestRegistryTest {
    @Test
    fun requestIsRegisteredBeforeDispatch() = runTest {
        val registry = RuntimeRequestRegistry(maximumPendingRequests = 4)
        val request = request("request-1")

        val result = registry.dispatchAndAwait(request, 1_000) {
            assertEquals(1, registry.pendingCount())
            registry.complete(successResponse(it))
        }

        assertTrue(result is RegistryResult.Response)
        assertEquals(0, registry.pendingCount())
    }

    @Test
    fun duplicateResponseCompletesRequestOnlyOnce() = runTest {
        val registry = RuntimeRequestRegistry(maximumPendingRequests = 4)
        val request = request("request-1")
        var secondCompletion: CompletionResult? = null

        val result = registry.dispatchAndAwait(request, 1_000) {
            val response = successResponse(it)
            assertEquals(CompletionResult.Completed, registry.complete(response))
            secondCompletion = registry.complete(response)
        }

        assertTrue(result is RegistryResult.Response)
        assertEquals(CompletionResult.AlreadyCompleted, secondCompletion)
    }

    @Test
    fun documentCancellationCompletesMatchingRequest() = runTest {
        val registry = RuntimeRequestRegistry(maximumPendingRequests = 4)
        val request = request("request-1")

        val result = async {
            registry.dispatchAndAwait(request, 5_000) { }
        }
        while (registry.pendingCount() == 0) yield()

        assertEquals(1, registry.cancelDocument(request.documentId, "navigation started"))
        assertEquals(RegistryResult.Cancelled("navigation started"), result.await())
    }

    @Test
    fun pendingLimitRejectsAdditionalRequest() = runTest {
        val registry = RuntimeRequestRegistry(maximumPendingRequests = 1)
        val first = async { registry.dispatchAndAwait(request("first"), 5_000) { } }
        while (registry.pendingCount() == 0) yield()

        val second = registry.dispatchAndAwait(request("second"), 1_000) { }

        assertEquals(RegistryResult.LimitExceeded(1), second)
        registry.cancelDocument(DocumentId("document-1"), "test finished")
        first.await()
    }

    @Test
    fun callerCancellationAlwaysRemovesPendingRequest() = runTest {
        val registry = RuntimeRequestRegistry(maximumPendingRequests = 1)
        val pending = async { registry.dispatchAndAwait(request("request-1"), 5_000) { } }
        while (registry.pendingCount() == 0) yield()

        pending.cancelAndJoin()

        assertEquals(0, registry.pendingCount())
    }

    private fun request(id: String) = RuntimeRequestEnvelope(
        bridgeToken = TEST_BRIDGE_TOKEN,
        sessionId = "session-1",
        documentId = DocumentId("document-1"),
        requestId = id,
        method = RuntimeMethods.PING,
    )

    private fun successResponse(request: RuntimeRequestEnvelope) = RuntimeResponseEnvelope(
        protocolVersion = RUNTIME_PROTOCOL_VERSION,
        bridgeToken = request.bridgeToken,
        sessionId = request.sessionId,
        documentId = request.documentId,
        requestId = request.requestId,
        status = RuntimeResponseStatus.SUCCESS,
        result = JsonNull,
    )

    private companion object {
        const val TEST_BRIDGE_TOKEN = "bridge-token-12345678901234567890123456789012"
    }
}
