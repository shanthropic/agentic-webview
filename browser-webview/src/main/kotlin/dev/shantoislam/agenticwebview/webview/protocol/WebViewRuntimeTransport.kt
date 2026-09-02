package dev.shantoislam.agenticwebview.webview.protocol

import android.webkit.WebView
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import org.json.JSONObject

internal class WebViewRuntimeTransport(
    private val webViewProvider: () -> WebView?,
) : RuntimeTransport {
    override suspend fun dispatch(encodedRequest: String) = withContext(Dispatchers.Main.immediate) {
        val webView = webViewProvider()
            ?: throw IllegalStateException("WebView is not attached")
        val requestLiteral = JSONObject.quote(encodedRequest)
        suspendCancellableCoroutine<Unit> { continuation ->
            webView.evaluateJavascript(
                """
                (function() {
                    try {
                        var runtime = window.__AgenticWebRuntime;
                        if (!runtime || typeof runtime.dispatchProtocol !== 'function') {
                            return 'ERROR:Agentic runtime protocol is unavailable';
                        }
                        runtime.dispatchProtocol($requestLiteral).then(
                            function(response) {
                                if (window.AgenticProtocolBridge) {
                                    window.AgenticProtocolBridge.onResponse(response);
                                }
                            },
                            function(error) {
                                if (window.AgenticProtocolBridge) {
                                    window.AgenticProtocolBridge.onTransportError(String(error));
                                }
                            }
                        );
                        return 'DISPATCHED';
                    } catch (error) {
                        return 'ERROR:' + String(error && error.message ? error.message : error);
                    }
                })();
                """.trimIndent(),
            ) { rawResult ->
                if (!continuation.isActive) return@evaluateJavascript
                val result = decodeJavascriptString(rawResult)
                if (result == "DISPATCHED") {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        IllegalStateException(result?.removePrefix("ERROR:") ?: "Runtime dispatch returned no result"),
                    )
                }
            }
        }
    }

    private fun decodeJavascriptString(raw: String?): String? {
        if (raw == null || raw == "null" || raw == "undefined") return null
        return try {
            when (val decoded = JSONTokener(raw).nextValue()) {
                JSONObject.NULL -> null
                is String -> decoded
                else -> decoded.toString()
            }
        } catch (_: Exception) {
            raw
        }
    }
}
