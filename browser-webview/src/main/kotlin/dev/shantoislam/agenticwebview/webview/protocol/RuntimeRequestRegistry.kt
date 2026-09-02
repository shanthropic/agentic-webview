package dev.shantoislam.agenticwebview.webview.protocol

import dev.shantoislam.agenticwebview.api.DocumentId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class RuntimeRequestRegistry(
    private val maximumPendingRequests: Int,
) {
    private val mutex = Mutex()
    private val pending = LinkedHashMap<String, PendingRequest>()
    private val closed = AtomicBoolean(false)

    init { require(maximumPendingRequests > 0) { "maximumPendingRequests must be positive" } }

    suspend fun dispatchAndAwait(
        request: RuntimeRequestEnvelope,
        timeoutMs: Long,
        dispatch: suspend (RuntimeRequestEnvelope) -> Unit,
    ): RegistryResult {
        require(timeoutMs > 0) { "timeoutMs must be positive" }

        val deferred = CompletableDeferred<RegistrySignal>()
        val registration = mutex.withLock {
            when {
                closed.get() -> RegistryResult.Closed
                request.requestId in pending -> RegistryResult.Rejected("Duplicate requestId: ${request.requestId}")
                pending.size >= maximumPendingRequests -> RegistryResult.LimitExceeded(maximumPendingRequests)
                else -> {
                    pending[request.requestId] = PendingRequest(request, deferred)
                    null
                }
            }
        }
        if (registration != null) return registration

        return try {
            dispatch(request)
            when (val signal = withTimeout(timeoutMs) { deferred.await() }) {
                is RegistrySignal.Response -> {
                    val validationError = signal.value.validationError(request)
                    if (validationError == null) RegistryResult.Response(signal.value)
                    else RegistryResult.Rejected(validationError)
                }
                is RegistrySignal.Cancelled -> RegistryResult.Cancelled(signal.reason)
            }
        } catch (_: TimeoutCancellationException) {
            RegistryResult.TimedOut(timeoutMs)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            RegistryResult.DispatchFailed(error.message ?: error::class.java.simpleName)
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { pending.remove(request.requestId) }
            }
        }
    }

    suspend fun complete(response: RuntimeResponseEnvelope): CompletionResult {
        val target = mutex.withLock { pending[response.requestId] }
            ?: return CompletionResult.UnknownRequest
        return if (target.deferred.complete(RegistrySignal.Response(response))) {
            CompletionResult.Completed
        } else {
            CompletionResult.AlreadyCompleted
        }
    }

    suspend fun cancelDocument(documentId: DocumentId, reason: String): Int {
        val targets = mutex.withLock {
            pending.values.filter { it.request.documentId == documentId }
        }
        targets.forEach { it.deferred.complete(RegistrySignal.Cancelled(reason)) }
        return targets.size
    }

    suspend fun close(reason: String = "Runtime request registry closed"): Int {
        if (!closed.compareAndSet(false, true)) return 0
        val targets = mutex.withLock { pending.values.toList() }
        targets.forEach { it.deferred.complete(RegistrySignal.Cancelled(reason)) }
        return targets.size
    }

    suspend fun pendingCount(): Int = mutex.withLock { pending.size }

    private data class PendingRequest(
        val request: RuntimeRequestEnvelope,
        val deferred: CompletableDeferred<RegistrySignal>,
    )
}

internal sealed interface RegistryResult {
    data class Response(val value: RuntimeResponseEnvelope) : RegistryResult
    data class Rejected(val reason: String) : RegistryResult
    data class LimitExceeded(val limit: Int) : RegistryResult
    data class TimedOut(val timeoutMs: Long) : RegistryResult
    data class Cancelled(val reason: String) : RegistryResult
    data class DispatchFailed(val reason: String) : RegistryResult
    data object Closed : RegistryResult
}

internal enum class CompletionResult { Completed, AlreadyCompleted, UnknownRequest }

private sealed interface RegistrySignal {
    data class Response(val value: RuntimeResponseEnvelope) : RegistrySignal
    data class Cancelled(val reason: String) : RegistrySignal
}
