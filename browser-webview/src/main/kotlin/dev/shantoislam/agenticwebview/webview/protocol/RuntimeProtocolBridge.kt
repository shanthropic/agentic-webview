package dev.shantoislam.agenticwebview.webview.protocol

import android.os.SystemClock
import android.webkit.JavascriptInterface
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class RuntimeProtocolBridge(
    private val scope: CoroutineScope,
    private val gatewayProvider: () -> RuntimeProtocolGateway?,
    private val maximumMessageBytes: Int,
    private val maximumConcurrentCallbacks: Int,
    private val onRejectedResponse: (String) -> Unit = {},
    private val transportErrorHandler: (String) -> Unit = {},
) {
    private val activeCallbacks = AtomicInteger(0)
    private val callbackRateLock = Any()
    private var callbackWindowStartedAtMs = SystemClock.elapsedRealtime()
    private var callbacksInWindow = 0
    private var rateLimitReported = false

    init {
        require(maximumMessageBytes > 0) { "maximumMessageBytes must be positive" }
        require(maximumConcurrentCallbacks > 0) { "maximumConcurrentCallbacks must be positive" }
    }

    @JavascriptInterface
    fun onResponse(rawResponse: String) {
        if (rawResponse.length > maximumMessageBytes) {
            onRejectedResponse("Runtime response exceeds the bridge message limit")
            return
        }
        if (!acquireCallback()) return
        scope.launch {
            try {
                when (val result = gatewayProvider()?.acceptResponse(rawResponse)) {
                    is IncomingResponseResult.Rejected -> onRejectedResponse(result.reason)
                    IncomingResponseResult.AlreadyCompleted -> onRejectedResponse("Runtime response was already completed")
                    IncomingResponseResult.UnknownRequest -> onRejectedResponse("Runtime response has an unknown requestId")
                    IncomingResponseResult.Completed -> Unit
                    null -> onRejectedResponse("Runtime gateway is unavailable")
                }
            } finally {
                activeCallbacks.decrementAndGet()
            }
        }
    }

    @JavascriptInterface
    fun onTransportError(message: String) {
        if (!acquireCallback()) return
        val boundedMessage = message.take(MAX_ERROR_MESSAGE_LENGTH)
        scope.launch {
            try {
                transportErrorHandler(boundedMessage)
            } finally {
                activeCallbacks.decrementAndGet()
            }
        }
    }

    private fun acquireCallback(): Boolean {
        val withinRateLimit = synchronized(callbackRateLock) {
            val now = SystemClock.elapsedRealtime()
            if (now - callbackWindowStartedAtMs >= CALLBACK_WINDOW_MS) {
                callbackWindowStartedAtMs = now
                callbacksInWindow = 0
                rateLimitReported = false
            }
            if (callbacksInWindow >= maxOf(MINIMUM_CALLBACKS_PER_WINDOW, maximumConcurrentCallbacks * 4)) {
                val shouldReport = !rateLimitReported
                rateLimitReported = true
                shouldReport to false
            } else {
                callbacksInWindow++
                false to true
            }
        }
        if (!withinRateLimit.second) {
            if (withinRateLimit.first) onRejectedResponse("Runtime callback rate limit exceeded")
            return false
        }
        if (activeCallbacks.incrementAndGet() <= maximumConcurrentCallbacks) return true
        activeCallbacks.decrementAndGet()
        onRejectedResponse("Runtime callback limit exceeded")
        return false
    }

    private companion object {
        const val MAX_ERROR_MESSAGE_LENGTH = 2_000
        const val CALLBACK_WINDOW_MS = 1_000L
        const val MINIMUM_CALLBACKS_PER_WINDOW = 16
    }
}
