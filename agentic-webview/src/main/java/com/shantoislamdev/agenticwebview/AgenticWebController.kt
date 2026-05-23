package com.shantoislamdev.agenticwebview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.webkit.ValueCallback
import com.shantoislamdev.agenticwebview.config.AgenticWebViewConfig
import com.shantoislamdev.agenticwebview.internal.SdkLogger
import com.shantoislamdev.agenticwebview.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class AgenticWebController(
    private val config: AgenticWebViewConfig = AgenticWebViewConfig()
) {
    private val logger = SdkLogger(config.enableDebugLogging)
    private var webView: AgenticWebView? = null
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var settlementJob: Job? = null

    fun attach(webView: AgenticWebView) {
        this.webView = webView
        webView.listener = object : AgenticWebView.AgenticWebViewListener {
            override fun onStateChanged(state: PageLifecycleState) {
                logger.d("Controller", "State changed: $state")
            }

            override fun onProgressChanged(progress: Int) {
                if (progress == 100) {
                    startSettlementTimer()
                }
            }

            override fun onDomMutated(json: String) {
                startSettlementTimer()
            }
        }
    }

    fun detach() {
        webView?.listener = null
        webView = null
        settlementJob?.cancel()
    }

    private fun startSettlementTimer() {
        settlementJob?.cancel()
        settlementJob = scope.launch {
            delay(config.pageSettleDebounceMs)
            webView?.updatePageState(PageLifecycleState.COMPLETE)
        }
    }

    suspend fun captureState(): AgentResult<AgentState> = mutex.withLock {
        val wv = webView ?: return AgentResult.Error(AgentError.PageNotReady(PageLifecycleState.IDLE))
        
        return try {
            val treeJson = evalJs("__AgenticInternal.getAccessibilityTree(${config.maxDomElements})")
            val treeObj = JSONObject(treeJson)
            val viewportJson = evalJs("__AgenticInternal.getViewportInfo()")
            val viewportObj = JSONObject(viewportJson)

            val screenshot = if (config.screenshotEnabled) captureScreenshot() else null

            AgentResult.Success(AgentState(
                accessibilityTree = treeObj.getString("tree"),
                screenshotBase64 = screenshot,
                viewportInfo = parseViewportInfo(viewportObj),
                url = wv.url ?: "",
                title = wv.title ?: "",
                pageState = wv.pageLifecycleState,
                elementCount = treeObj.getJSONArray("tree").length(),
                truncated = treeObj.getBoolean("truncated")
            ))
        } catch (e: Exception) {
            AgentResult.Error(AgentError.JsEvaluationFailed(e.message ?: "Unknown error"))
        }
    }

    suspend fun executeAction(action: AgentAction): AgentResult<Unit> = mutex.withLock {
        repeat(config.actionRetryCount + 1) { attempt ->
            val result = executeActionInternal(action)
            if (result is AgentResult.Success) return result
            if (attempt == config.actionRetryCount) return result
            delay(500)
        }
        return AgentResult.Error(AgentError.Timeout("Action execution", 0))
    }

    private suspend fun executeActionInternal(action: AgentAction): AgentResult<Unit> {
        val wv = webView ?: return AgentResult.Error(AgentError.PageNotReady(PageLifecycleState.IDLE))

        return when (action) {
            is AgentAction.Click -> handleClick(action.agentId)
            is AgentAction.LongPress -> handleLongPress(action.agentId, action.durationMs)
            is AgentAction.InputText -> handleInputText(action.agentId, action.text, action.clearFirst)
            is AgentAction.SelectOption -> handleSelectOption(action.agentId, action.value)
            is AgentAction.Scroll -> handleScroll(action.direction, action.amount)
            is AgentAction.Navigate -> handleNavigate(action.url)
            is AgentAction.GoBack -> { wv.goBack(); AgentResult.Success(Unit) }
            is AgentAction.GoForward -> { wv.goForward(); AgentResult.Success(Unit) }
            is AgentAction.Refresh -> { wv.reload(); AgentResult.Success(Unit) }
            is AgentAction.Wait -> { delay(action.durationMs); AgentResult.Success(Unit) }
        }
    }

    private suspend fun handleClick(agentId: String): AgentResult<Unit> {
        val coords = getElementCoords(agentId) ?: return AgentResult.Error(AgentError.ElementNotFound(agentId))
        dispatchTouch(coords.first, coords.second)
        return AgentResult.Success(Unit)
    }

    private suspend fun handleLongPress(agentId: String, durationMs: Long): AgentResult<Unit> {
        val coords = getElementCoords(agentId) ?: return AgentResult.Error(AgentError.ElementNotFound(agentId))
        dispatchTouch(coords.first, coords.second, durationMs)
        return AgentResult.Success(Unit)
    }

    private suspend fun handleInputText(agentId: String, text: String, clearFirst: Boolean): AgentResult<Unit> {
        val wv = webView ?: return AgentResult.Error(AgentError.PageNotReady(PageLifecycleState.IDLE))
        val coords = getElementCoords(agentId) ?: return AgentResult.Error(AgentError.ElementNotFound(agentId))
        
        // Focus first
        dispatchTouch(coords.first, coords.second)
        delay(200)

        val success = evalJs("__AgenticInternal.setInputValue('$agentId', '$text')").toBoolean()
        return if (success) AgentResult.Success(Unit) else AgentResult.Error(AgentError.JsEvaluationFailed("Failed to set input value"))
    }

    private suspend fun handleSelectOption(agentId: String, value: String): AgentResult<Unit> {
        val success = evalJs("__AgenticInternal.setSelectOption('$agentId', '$value')").toBoolean()
        return if (success) AgentResult.Success(Unit) else AgentResult.Error(AgentError.JsEvaluationFailed("Failed to set select option"))
    }

    private suspend fun handleScroll(direction: ScrollDirection, amount: Float): AgentResult<Unit> {
        val script = when (direction) {
            ScrollDirection.UP -> "window.scrollBy(0, -window.innerHeight * $amount)"
            ScrollDirection.DOWN -> "window.scrollBy(0, window.innerHeight * $amount)"
            ScrollDirection.LEFT -> "window.scrollBy(-window.innerWidth * $amount, 0)"
            ScrollDirection.RIGHT -> "window.scrollBy(window.innerWidth * $amount, 0)"
        }
        evalJs(script)
        return AgentResult.Success(Unit)
    }

    private suspend fun handleNavigate(url: String): AgentResult<Unit> {
        val wv = webView ?: return AgentResult.Error(AgentError.PageNotReady(PageLifecycleState.IDLE))
        wv.loadUrl(url)
        
        // Wait for completion or timeout
        return withTimeoutOrNull(config.pageSettleTimeoutMs) {
            while (wv.pageLifecycleState != PageLifecycleState.COMPLETE) {
                delay(100)
            }
            AgentResult.Success(Unit)
        } ?: AgentResult.Error(AgentError.Timeout("Navigation", config.pageSettleTimeoutMs))
    }

    private suspend fun getElementCoords(agentId: String): Pair<Float, Float>? {
        evalJs("__AgenticInternal.scrollIntoView('$agentId')")
        delay(300) // Wait for scroll animation
        val json = evalJs("__AgenticInternal.getElementCenter('$agentId')")
        if (json == "null") return null
        val obj = JSONObject(json)
        return Pair(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
    }

    private fun dispatchTouch(x: Float, y: Float, durationMs: Long = 0) {
        val wv = webView ?: return
        val downTime = System.currentTimeMillis()
        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        wv.dispatchTouchEvent(downEvent)
        
        if (durationMs > 0) {
            Thread.sleep(durationMs)
        }

        val upTime = downTime + durationMs + 50
        val upEvent = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)
        wv.dispatchTouchEvent(upEvent)
    }

    private suspend fun evalJs(script: String): String = withTimeout(config.jsEvaluationTimeoutMs) {
        suspendCancellableCoroutine { cont ->
            webView?.evaluateJavascript(script) { result ->
                // evaluateJavascript returns JSON-formatted string (e.g. "\"value\"")
                val cleanResult = if (result != null && result.startsWith("\"") && result.endsWith("\"")) {
                    result.substring(1, result.length - 1).replace("\\\"", "\"")
                } else {
                    result ?: ""
                }
                cont.resume(cleanResult)
            } ?: cont.resume("")
        }
    }

    private fun captureScreenshot(): String? {
        val wv = webView ?: return null
        val bitmap = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        wv.draw(canvas)
        
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, config.screenshotQuality, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun parseViewportInfo(obj: JSONObject): ViewportInfo {
        return ViewportInfo(
            devicePixelRatio = obj.getDouble("devicePixelRatio"),
            visualViewportScale = obj.getDouble("visualViewportScale"),
            scrollX = obj.getInt("scrollX"),
            scrollY = obj.getInt("scrollY"),
            viewportWidth = obj.getInt("viewportWidth"),
            viewportHeight = obj.getInt("viewportHeight")
        )
    }

    fun destroy() {
        scope.cancel()
    }

    fun pauseTimers() {
        webView?.pauseTimers()
    }

    fun resumeTimers() {
        webView?.resumeTimers()
    }
}
