package com.shantoislamdev.agenticwebview

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.*
import android.util.Base64
import android.view.MotionEvent
import android.view.PixelCopy
import androidx.core.graphics.createBitmap
import com.shantoislamdev.agenticwebview.config.AgenticWebViewConfig
import com.shantoislamdev.agenticwebview.internal.SdkLogger
import com.shantoislamdev.agenticwebview.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
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

    var lastDropdownOptions: String? = null
        private set

    private val _state = MutableStateFlow<AgentState?>(null)
    val state: StateFlow<AgentState?> = _state.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress: StateFlow<Int> = _loadingProgress.asStateFlow()

    // ─── Attach / Detach ──────────────────────────────────────────────

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
                _loadingProgress.value = progress
                if (progress == 100) {
                    startSettlementTimer()
                }
            }

            override fun onDomMutated(json: String) {
                startSettlementTimer()
                // Only capture state if page is in a stable lifecycle phase
                val currentPhase = this@AgenticWebController.webView?.pageLifecycleState
                if (currentPhase == PageLifecycleState.COMPLETE ||
                    currentPhase == PageLifecycleState.INTERACTIVE) {
                    scope.launch {
                        val currentState = captureState()
                        if (currentState is AgentResult.Success) {
                            _state.value = currentState.data
                        }
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

    /**
     * Core evaluateJavascript wrapper. Returns the raw string from the JS engine.
     * Returns empty string on any failure (null callback, timeout, detached WebView).
     * Never returns the literal string "null".
     *
     * The evaluateJavascript callback returns a JSON-serialized representation of
     * the JS result. We use JSONTokener to decode it properly, handling all JSON
     * escape sequences (\\, \n, \t, \uXXXX, \", etc.).
     */
    private suspend fun evalJsRaw(script: String): String = withContext(Dispatchers.Main) {
        try {
            withTimeout(config.jsEvaluationTimeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val wv = webView
                    if (wv == null) {
                        cont.resume("")
                        return@suspendCancellableCoroutine
                    }
                    wv.evaluateJavascript(script) { result ->
                        when {
                            result == null -> {
                                logger.d("Controller", "JS returned null for: ${script.take(80)}")
                                cont.resume("")
                            }
                            result == "null" -> cont.resume("")
                            result == "undefined" -> cont.resume("")
                            result.startsWith("\"") && result.endsWith("\"") && result.length >= 2 -> {
                                // Result is a JSON-encoded string. Use JSONTokener for proper decoding.
                                try {
                                    val decoded = org.json.JSONTokener(result).nextValue() as String
                                    cont.resume(decoded)
                                } catch (e: Exception) {
                                    // Fallback: manual unescape for critical characters
                                    cont.resume(
                                        result.substring(1, result.length - 1)
                                            .replace("\\\\", "\\")
                                            .replace("\\\"", "\"")
                                            .replace("\\n", "\n")
                                            .replace("\\t", "\t")
                                            .replace("\\/", "/")
                                    )
                                }
                            }
                            else -> cont.resume(result)
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            logger.w("Controller", "JS evaluation timed out: ${script.take(80)}")
            ""
        } catch (e: Exception) {
            logger.e("Controller", "JS evaluation failed: ${script.take(80)}", e)
            ""
        }
    }

    /**
     * Evaluates JS and parses the result as a JSONObject.
     * Returns null if the result is not valid JSON (null, empty, malformed).
     */
    private suspend fun evalJsJson(script: String): JSONObject? {
        val raw = evalJsRaw(script)
        if (raw.isBlank()) return null
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            logger.w("Controller", "Failed to parse JSON from JS: ${raw.take(100)}")
            null
        }
    }

    /**
     * Evaluates JS and returns the result as a boolean.
     * Returns false on any failure.
     */
    private suspend fun evalJsBool(script: String): Boolean {
        val raw = evalJsRaw(script)
        return raw.equals("true", ignoreCase = true)
    }

    /**
     * Fire-and-forget JS evaluation. Ignores the result entirely.
     */
    private suspend fun evalJsVoid(script: String) {
        evalJsRaw(script)
    }

    // ─── State Capture ────────────────────────────────────────────────

    suspend fun captureState(): AgentResult<AgentState> = mutex.withLock {
        val wv = webView ?: return AgentResult.Error(AgentError.PageNotReady(PageLifecycleState.IDLE))

        // Try to get the accessibility tree. If it fails, inject script and retry.
        var treeObj = evalJsJson("__AgenticInternal.getAccessibilityTree(${config.maxDomElements})")
        if (treeObj == null) {
            // Await full script injection (suspends until JS engine confirms availability or returns error)
            val injectionError = wv.ensureEngineAvailable()
            if (injectionError.isNotEmpty()) {
                return AgentResult.Error(AgentError.JsEvaluationFailed(injectionError))
            }

            // Retry with backoff — the DOM may still be settling after injection
            var retryDelay = 100L
            for (attempt in 1..3) {
                treeObj = evalJsJson("__AgenticInternal.getAccessibilityTree(${config.maxDomElements})")
                if (treeObj != null) break
                if (attempt < 3) {
                    delay(retryDelay)
                    retryDelay *= 2
                }
            }
            if (treeObj == null) {
                return AgentResult.Error(AgentError.JsEvaluationFailed("JS engine initialized successfully, but getAccessibilityTree returned null."))
            }
        }

        return try {

            // Critical: viewport info
            val viewportObj = evalJsJson("__AgenticInternal.getViewportInfo()")
                ?: return AgentResult.Error(AgentError.JsEvaluationFailed("Viewport info returned null"))

            // Screenshot (optional, already handles failures internally)
            val screenshot = if (config.screenshotEnabled) captureScreenshot() else null

            // Optional: selector map (graceful degradation)
            val selectorMapObj = evalJsJson("__AgenticInternal.getSelectorMap()")
            val selectorMap = mutableMapOf<String, String>()
            selectorMapObj?.keys()?.forEach { key ->
                selectorMap[key] = selectorMapObj.optString(key, "")
            }

            // Optional: compact tree (graceful degradation)
            val compactTree = evalJsRaw("__AgenticInternal.getCompactTree(${config.maxDomElements})")
                .ifBlank { null }

            AgentResult.Success(AgentState(
                accessibilityTree = treeObj.optString("tree", "[]"),
                screenshotBase64 = screenshot,
                viewportInfo = parseViewportInfo(viewportObj),
                url = withContext(Dispatchers.Main) { wv.url } ?: "",
                title = withContext(Dispatchers.Main) { wv.title } ?: "",
                pageState = withContext(Dispatchers.Main) { wv.pageLifecycleState },
                elementCount = treeObj.optJSONArray("tree")?.length() ?: 0,
                truncated = treeObj.optBoolean("truncated", false),
                selectorMap = selectorMap.ifEmpty { null },
                compactTree = compactTree
            ))
        } catch (e: Exception) {
            logger.e("Controller", "captureState failed", e)
            AgentResult.Error(AgentError.JsEvaluationFailed(e.message ?: "Unknown error"))
        }
    }

    // ─── Action Execution ─────────────────────────────────────────────

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
            is AgentAction.SendKeys -> handleSendKeys(action.keys)
            is AgentAction.ScrollToPercent -> handleScrollToPercent(action.yPercent, action.agentId)
            is AgentAction.ScrollToText -> handleScrollToText(action.text, action.nth)
            is AgentAction.ScrollToTop -> handleScrollToTop()
            is AgentAction.ScrollToBottom -> handleScrollToBottom()
            is AgentAction.PreviousPage -> handlePreviousPage()
            is AgentAction.NextPage -> handleNextPage()
            is AgentAction.GetDropdownOptions -> handleGetDropdownOptions(action.agentId)
            is AgentAction.SelectDropdownOption -> handleSelectDropdownOption(action.agentId, action.text)
        }
    }

    // ─── Action Handlers ──────────────────────────────────────────────

    private suspend fun handleClick(agentId: String): AgentResult<Unit> {
        val coords = getElementCoords(agentId) ?: return AgentResult.Error(AgentError.ElementNotFound(agentId))
        withContext(Dispatchers.Main) { dispatchTouch(coords.first, coords.second) }
        return AgentResult.Success(Unit)
    }

    private suspend fun handleLongPress(agentId: String, durationMs: Long): AgentResult<Unit> {
        val coords = getElementCoords(agentId) ?: return AgentResult.Error(AgentError.ElementNotFound(agentId))
        withContext(Dispatchers.Main) { dispatchTouch(coords.first, coords.second, durationMs) }
        return AgentResult.Success(Unit)
    }

    private suspend fun handleInputText(agentId: String, text: String, clearFirst: Boolean): AgentResult<Unit> {
        val coords = getElementCoords(agentId) ?: return AgentResult.Error(AgentError.ElementNotFound(agentId))
        withContext(Dispatchers.Main) { dispatchTouch(coords.first, coords.second) }
        delay(200)
        val textJson = JSONObject.quote(text)
        val success = evalJsBool("__AgenticInternal.setInputValue('$agentId', $textJson)")
        return if (success) AgentResult.Success(Unit)
        else AgentResult.Error(AgentError.JsEvaluationFailed("Failed to set input value"))
    }

    private suspend fun handleSelectOption(agentId: String, value: String): AgentResult<Unit> {
        val valueJson = JSONObject.quote(value)
        val success = evalJsBool("__AgenticInternal.setSelectOption('$agentId', $valueJson)")
        return if (success) AgentResult.Success(Unit)
        else AgentResult.Error(AgentError.JsEvaluationFailed("Failed to set select option"))
    }

    private suspend fun handleSendKeys(keys: String): AgentResult<Unit> {
        val keysJson = JSONObject.quote(keys)
        val success = evalJsBool("__AgenticInternal.sendKeys($keysJson)")
        return if (success) AgentResult.Success(Unit)
        else AgentResult.Error(AgentError.JsEvaluationFailed("Failed to send keys"))
    }

    private suspend fun handleScrollToPercent(yPercent: Float, agentId: String?): AgentResult<Unit> {
        val agentIdArg = if (agentId != null) "'$agentId'" else "undefined"
        evalJsVoid("__AgenticInternal.scrollToPercent($yPercent, $agentIdArg)")
        return AgentResult.Success(Unit)
    }

    private suspend fun handleScrollToText(text: String, nth: Int): AgentResult<Unit> {
        val textJson = JSONObject.quote(text)
        val success = evalJsBool("__AgenticInternal.scrollToText($textJson, $nth)")
        return if (success) AgentResult.Success(Unit)
        else AgentResult.Error(AgentError.ElementNotFound("text:$text"))
    }

    private suspend fun handleScrollToTop(): AgentResult<Unit> {
        evalJsVoid("__AgenticInternal.scrollToTop()")
        return AgentResult.Success(Unit)
    }

    private suspend fun handleScrollToBottom(): AgentResult<Unit> {
        evalJsVoid("__AgenticInternal.scrollToBottom()")
        return AgentResult.Success(Unit)
    }

    private suspend fun handlePreviousPage(): AgentResult<Unit> {
        evalJsVoid("__AgenticInternal.previousPage()")
        return AgentResult.Success(Unit)
    }

    private suspend fun handleNextPage(): AgentResult<Unit> {
        evalJsVoid("__AgenticInternal.nextPage()")
        return AgentResult.Success(Unit)
    }

    private suspend fun handleGetDropdownOptions(agentId: String): AgentResult<Unit> {
        val result = evalJsRaw("__AgenticInternal.getDropdownOptions('$agentId')")
        lastDropdownOptions = result.ifBlank { "[]" }
        logger.d("Controller", "Dropdown options: $lastDropdownOptions")
        return AgentResult.Success(Unit)
    }

    suspend fun getDropdownOptions(agentId: String): AgentResult<List<DropdownOption>> = mutex.withLock {
        return try {
            val json = evalJsRaw("__AgenticInternal.getDropdownOptions('$agentId')")
            lastDropdownOptions = json.ifBlank { "[]" }
            val arr = JSONArray(lastDropdownOptions)
            val options = mutableListOf<DropdownOption>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                options.add(DropdownOption(
                    value = obj.optString("value", ""),
                    text = obj.optString("text", ""),
                    index = obj.optInt("index", i)
                ))
            }
            AgentResult.Success(options)
        } catch (e: Exception) {
            AgentResult.Error(AgentError.JsEvaluationFailed(e.message ?: "Failed to parse dropdown options"))
        }
    }

    private suspend fun handleSelectDropdownOption(agentId: String, text: String): AgentResult<Unit> {
        val textJson = JSONObject.quote(text)
        val success = evalJsBool("__AgenticInternal.selectDropdownOption('$agentId', $textJson)")
        return if (success) AgentResult.Success(Unit)
        else AgentResult.Error(AgentError.JsEvaluationFailed("Failed to select dropdown option"))
    }

    private suspend fun handleScroll(direction: ScrollDirection, amount: Float): AgentResult<Unit> {
        val script = when (direction) {
            ScrollDirection.UP -> "window.scrollBy(0, -window.innerHeight * $amount)"
            ScrollDirection.DOWN -> "window.scrollBy(0, window.innerHeight * $amount)"
            ScrollDirection.LEFT -> "window.scrollBy(-window.innerWidth * $amount, 0)"
            ScrollDirection.RIGHT -> "window.scrollBy(window.innerWidth * $amount, 0)"
        }
        evalJsVoid(script)
        return AgentResult.Success(Unit)
    }

    private suspend fun handleNavigate(url: String): AgentResult<Unit> {
        val wv = webView ?: return AgentResult.Error(AgentError.PageNotReady(PageLifecycleState.IDLE))
        withContext(Dispatchers.Main) { wv.loadUrl(url) }

        return withTimeoutOrNull(config.pageSettleTimeoutMs) {
            while (true) {
                val state = withContext(Dispatchers.Main) { wv.pageLifecycleState }
                if (state == PageLifecycleState.COMPLETE) break
                delay(100)
            }
            AgentResult.Success(Unit)
        } ?: AgentResult.Error(AgentError.Timeout("Navigation", config.pageSettleTimeoutMs))
    }

    // ─── Element Coordinates ──────────────────────────────────────────

    private suspend fun getElementCoords(agentId: String): Pair<Float, Float>? {
        val promiseId = java.util.UUID.randomUUID().toString()
        evalJsVoid("__AgenticInternal.scrollIntoView('$agentId', '$promiseId')")

        val deferred = CompletableDeferred<String>()
        pendingPromises[promiseId] = deferred

        return try {
            val result = withTimeout(5000) { deferred.await() }
            if (result != "true") return null

            val json = evalJsJson("__AgenticInternal.getElementCenter('$agentId')")
                ?: return null
            val x = json.optDouble("x", Double.NaN)
            val y = json.optDouble("y", Double.NaN)
            if (x.isNaN() || y.isNaN()) return null
            Pair(x.toFloat(), y.toFloat())
        } catch (e: Exception) {
            logger.e("Controller", "Failed to resolve scroll promise", e)
            null
        } finally {
            pendingPromises.remove(promiseId)
        }
    }

    private fun onPromiseResolved(promiseId: String, result: String) {
        pendingPromises[promiseId]?.complete(result)
    }

    // ─── Touch Dispatch ───────────────────────────────────────────────

    private suspend fun dispatchTouch(x: Float, y: Float, durationMs: Long = 0) = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext
        val downTime = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        wv.dispatchTouchEvent(downEvent)

        if (durationMs > 0) { delay(durationMs) }

        val upTime = SystemClock.uptimeMillis()
        val upEvent = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)
        wv.dispatchTouchEvent(upEvent)
    }

    // ─── Screenshot ───────────────────────────────────────────────────

    private suspend fun captureScreenshot(): String? {
        val wv = webView ?: return null
        if (wv.width <= 0 || wv.height <= 0) return null

        val window = (wv.context as? Activity)?.window ?: return null
        val bitmap = getReusableBitmap(wv.width, wv.height)

        val locationInWindow = IntArray(2)
        wv.getLocationInWindow(locationInWindow)
        val sourceRect = Rect(
            locationInWindow[0], locationInWindow[1],
            locationInWindow[0] + wv.width, locationInWindow[1] + wv.height
        )

        return try {
            val result = suspendCancellableCoroutine<Int> { cont ->
                try {
                    PixelCopy.request(window, sourceRect, bitmap, { cont.resume(it) }, pixelCopyHandler)
                } catch (e: Exception) { cont.resume(-1) }
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
        if (current != null && current.width == width && current.height == height) return current
        current?.recycle()
        val newBitmap = createBitmap(width, height)
        cachedBitmap = newBitmap
        return newBitmap
    }

    // ─── Utilities ────────────────────────────────────────────────────

    private fun parseViewportInfo(obj: JSONObject): ViewportInfo {
        return ViewportInfo(
            devicePixelRatio = obj.optDouble("devicePixelRatio", 1.0),
            visualViewportScale = obj.optDouble("visualViewportScale", 1.0),
            scrollX = obj.optInt("scrollX", 0),
            scrollY = obj.optInt("scrollY", 0),
            viewportWidth = obj.optInt("viewportWidth", 0),
            viewportHeight = obj.optInt("viewportHeight", 0)
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

    fun pauseTimers() { webView?.pauseTimers() }
    fun resumeTimers() { webView?.resumeTimers() }
}
