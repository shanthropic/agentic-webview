package com.shantoislamdev.agenticwebview.internal

import com.shantoislamdev.agenticwebview.AgenticWebView
import com.shantoislamdev.agenticwebview.models.AgentError
import com.shantoislamdev.agenticwebview.models.AgentResult
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.coroutines.resume

internal class JsEvaluator(
    private val webViewProvider: () -> AgenticWebView?,
    private val timeoutMs: Long,
    private val logger: SdkLogger
) {
    suspend fun evalRaw(script: String): AgentResult<String> {
        return try {
            val result = withContext(Dispatchers.Main) {
                withTimeout(timeoutMs) {
                    suspendCancellableCoroutine<String> { cont ->
                        val wv = webViewProvider()
                        if (wv == null) {
                            cont.resume("")
                            return@suspendCancellableCoroutine
                        }
                        wv.evaluateJavascript(script) { jsResult ->
                            when {
                                jsResult == null -> {
                                    logger.d("JsEval", "JS returned null for: ${script.take(80)}")
                                    cont.resume("")
                                }
                                jsResult == "null" -> cont.resume("")
                                jsResult == "undefined" -> cont.resume("")
                                jsResult.startsWith("\"") && jsResult.endsWith("\"") && jsResult.length >= 2 -> {
                                    try {
                                        val decoded = org.json.JSONTokener(jsResult).nextValue() as String
                                        cont.resume(decoded)
                                    } catch (e: Exception) {
                                        cont.resume(
                                            jsResult.substring(1, jsResult.length - 1)
                                                .replace("\\\\", "\\")
                                                .replace("\\\"", "\"")
                                                .replace("\\n", "\n")
                                                .replace("\\t", "\t")
                                                .replace("\\/", "/")
                                        )
                                    }
                                }
                                else -> cont.resume(jsResult)
                            }
                        }
                    }
                }
            }
            AgentResult.Success(result)
        } catch (e: TimeoutCancellationException) {
            logger.w("JsEval", "JS evaluation timed out: ${script.take(80)}")
            AgentResult.Error(AgentError.JsEvaluationTimeout(timeoutMs))
        } catch (e: Exception) {
            logger.e("JsEval", "JS evaluation failed: ${script.take(80)}", e)
            AgentResult.Error(AgentError.JsEvaluationFailed(e.message ?: "Unknown error"))
        }
    }

    suspend fun evalJson(script: String): JSONObject? {
        return when (val result = evalRaw(script)) {
            is AgentResult.Success -> {
                if (result.data.isBlank()) null
                else try { JSONObject(result.data) } catch (e: Exception) {
                    logger.w("JsEval", "Failed to parse JSON: ${result.data.take(100)}")
                    null
                }
            }
            is AgentResult.Error -> null
        }
    }

    suspend fun evalBool(script: String): Boolean {
        return when (val result = evalRaw(script)) {
            is AgentResult.Success -> result.data.equals("true", ignoreCase = true)
            is AgentResult.Error -> false
        }
    }

    suspend fun evalVoid(script: String) {
        evalRaw(script)
    }
}
