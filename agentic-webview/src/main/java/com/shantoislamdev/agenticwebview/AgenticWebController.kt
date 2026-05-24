package com.shantoislamdev.agenticwebview

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.*
import android.util.Base64
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.webkit.ValueCallback
import com.shantoislamdev.agenticwebview.config.AgenticWebViewConfig
import com.shantoislamdev.agenticwebview.internal.SdkLogger
import com.shantoislamdev.agenticwebview.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    
    private val pendingPromises = mutableMapOf<String, CompletableDeferred<String>>()

    private val pixelCopyThread = HandlerThread("PixelCopyThread").apply { start() }
    private val pixelCopyHandler = Handler(pixelCopyThread.looper)

    private var cachedBitmap: Bitmap? = null

    private val _state = MutableStateFlow<AgentState?>(null)
    val state: StateFlow<AgentState?> = _state.asStateFlow()

    fun attach(webView: AgenticWebView) {
        this.webView = webView
        webView.listener = object : AgenticWebView.AgenticWebViewListener {
            override fun onStateChanged(state: PageLifecycleState) {
                logger.d("Controller", "State changed: $state")
                if (state == PageLifecycleState.LOADING ||
                    state == PageLifecycleState.CRASHED ||
                    state == PageLifecycleState.ERROR) {
                    cancelAllPendingPromises("Page lifecycle changed to $state")
                }
            }

            override fun onProgressChanged(progress: Int) {
                if (progress == 100) {
                    startSettlementTimer()
                }
            }

            override fun onDomMutated(json: String) {
                startSettlementTimer()
                scope.launch {
                    val currentState = captureState()
                    if (currentState is AgentResult.Success) {
                        _state.value = currentState.data
                    }
                }
            }

            override fun onPromiseResolved(promiseId: String, result: String) {
                this@AgenticWebController.onPromiseResolved(promiseId, result)
            }

            override fun onCrash(didRecover: Boolean) {
                logger.e("Controller", "WebView crashed. Recovery attempted: $didRecover")
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
        val promiseId = java.util.UUID.randomUUID().toString()
        evalJs("__AgenticInternal.scrollIntoView('$agentId', '$promiseId')")
        
        val deferred = CompletableDeferred<String>()
        pendingPromises[promiseId] = deferred
        
        return try {
            val result = withTimeout(5000) { deferred.await() }
            if (result != "true") return null
            
            val json = evalJs("__AgenticInternal.getElementCenter('$agentId')")
            if (json == "null") return null
            val obj = JSONObject(json)
            Pair(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
        } catch (e: Exception) {
            logger.e("Controller", "Failed to resolve scroll promise", e)
            null
        } finally {
            pendingPromises.remove(promiseId)
        }
    }

    fun onPromiseResolved(promiseId: String, result: String) {
        pendingPromises[promiseId]?.complete(result)
    }

    private suspend fun dispatchTouch(x: Float, y: Float, durationMs: Long = 0) {
        val wv = webView ?: return
        val downTime = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        wv.dispatchTouchEvent(downEvent)
        
        if (durationMs > 0) {
            delay(durationMs)
        }

        val upTime = SystemClock.uptimeMillis()
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

    private suspend fun captureScreenshot(): String? {
        val wv = webView ?: return null
        
        if (wv.width <= 0 || wv.height <= 0) return null

        val window = (wv.context as? Activity)?.window ?: return null
        val bitmap = getReusableBitmap(wv.width, wv.height)
        
        val locationInWindow = IntArray(2)
        wv.getLocationInWindow(locationInWindow)
        val sourceRect = Rect(
            locationInWindow[0],
            locationInWindow[1],
            locationInWindow[0] + wv.width,
            locationInWindow[1] + wv.height
        )

        return try {
            val result = suspendCancellableCoroutine<Int> { cont ->
                try {
                    PixelCopy.request(window, sourceRect, bitmap, { copyResult ->
                        cont.resume(copyResult)
                    }, pixelCopyHandler)
                } catch (e: Exception) {
                    cont.resume(-1) // Signal error
                }
            }

            if (result == PixelCopy.SUCCESS) {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, config.screenshotQuality, outputStream)
                Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            } else {
                logger.e("Controller", "PixelCopy failed with code: $result")
                null
            }
        } catch (e: Exception) {
            logger.e("Controller", "Failed to capture screenshot", e)
            null
        }
    }

    private fun getReusableBitmap(width: Int, height: Int): Bitmap {
        val current = cachedBitmap
        if (current != null && current.width == width && current.height == height) {
            return current
        }
        current?.recycle()
        val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        cachedBitmap = newBitmap
        return newBitmap
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

    private fun cancelAllPendingPromises(reason: String) {
        if (pendingPromises.isEmpty()) return
        logger.w("Controller", "Cancelling all pending promises: $reason")
        val promises = HashMap(pendingPromises)
        pendingPromises.clear()
        for ((_, deferred) in promises) {
            deferred.complete("error:page_transition:$reason")
        }
    }

    fun destroy() {
        scope.cancel()
        pixelCopyThread.quitSafely()
        cachedBitmap?.recycle()
        cachedBitmap = null
        cancelAllPendingPromises("Controller destroyed")
    }

    fun pauseTimers() {
        webView?.pauseTimers()
    }

    fun resumeTimers() {
        webView?.resumeTimers()
    }
}
