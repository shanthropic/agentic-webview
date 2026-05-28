package com.shantoislamdev.agenticwebview

import android.os.*
import android.view.MotionEvent
import com.shantoislamdev.agenticwebview.config.AgenticWebViewConfig
import com.shantoislamdev.agenticwebview.internal.JsEvaluator
import com.shantoislamdev.agenticwebview.internal.ScreenshotCapture
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

class AgenticWebController(
    private val config: AgenticWebViewConfig = AgenticWebViewConfig()
) {
    private val logger = SdkLogger(config.enableDebugLogging)
    private var webView: AgenticWebView? = null
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var settlementJob: Job? = null

    private val pendingPromises = mutableMapOf<String, CompletableDeferred<String>>()

    private val jsEvaluator = JsEvaluator(
        webViewProvider = { webView },
        timeoutMs = config.jsEvaluationTimeoutMs,
        logger = logger
    )

    private val screenshotCapture = ScreenshotCapture(
        webViewProvider = { webView },
        quality = config.screenshotQuality,
        maxDimension = config.screenshotMaxDimension,
        logger = logger
    )

    var lastDropdownOptions: String? = null
        private set

    private val occludedMap = mutableMapOf<String, Boolean>()

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
                _state.value = null
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

    // ─── JS Evaluation (delegated to JsEvaluator) ──────────────────

    private suspend fun evalJsRaw(script: String): AgentResult<String> =
        jsEvaluator.evalRaw(script)

    private suspend fun evalJsJson(script: String): JSONObject? =
        jsEvaluator.evalJson(script)

    private suspend fun evalJsBool(script: String): Boolean =
        jsEvaluator.evalBool(script)

    private suspend fun evalJsVoid(script: String) =
        jsEvaluator.evalVoid(script)

    // ─── State Capture ────────────────────────────────────────────────

    suspend fun captureState(): AgentResult<AgentState> = mutex.withLock {
        val wv = webView ?: return AgentResult.Error(AgentError.PageNotReady(PageLifecycleState.IDLE))

        var fullCaptureObj = evalJsJson("__AgenticInternal.getFullCapture(${config.maxDomElements})")
        if (fullCaptureObj == null) {
            val injectionError = wv.ensureEngineAvailable()
            if (injectionError.isNotEmpty()) {
                return AgentResult.Error(AgentError.JsEvaluationFailed(injectionError))
            }

            var retryDelay = 100L
            for (attempt in 1..3) {
                fullCaptureObj = evalJsJson("__AgenticInternal.getFullCapture(${config.maxDomElements})")
                if (fullCaptureObj != null) break
                if (attempt < 3) {
                    delay(retryDelay)
                    retryDelay *= 2
                }
            }
            if (fullCaptureObj == null) {
                return AgentResult.Error(AgentError.JsEvaluationFailed("JS engine initialized, but getFullCapture returned null."))
            }
        }

        return try {
            val viewportObj = evalJsJson("__AgenticInternal.getViewportInfo()")
                ?: return AgentResult.Error(AgentError.JsEvaluationFailed("Viewport info returned null"))

            val screenshot = if (config.screenshotEnabled) {
                when (val result = captureScreenshot()) {
                    is AgentResult.Success -> result.data
                    is AgentResult.Error -> null
                }
            } else null

            val treeArr = fullCaptureObj.optJSONArray("tree")
            occludedMap.clear()
            if (treeArr != null) {
                for (i in 0 until treeArr.length()) {
                    val node = treeArr.optJSONObject(i) ?: continue
                    val id = node.optString("id", "")
                    if (id.isNotEmpty()) {
                        occludedMap[id] = node.optBoolean("occluded", false)
                    }
                }
            }

            val selectorMapObj = fullCaptureObj.optJSONObject("selectorMap")
            val selectorMap = mutableMapOf<String, String>()
            selectorMapObj?.keys()?.forEach { key ->
                selectorMap[key] = selectorMapObj.optString(key, "")
            }

            val compactTree = fullCaptureObj.optString("compactTree", "").ifBlank { null }

            AgentResult.Success(AgentState(
                accessibilityTree = fullCaptureObj.optString("tree", "[]"),
                screenshotBase64 = screenshot,
                viewportInfo = parseViewportInfo(viewportObj),
                url = withContext(Dispatchers.Main) { wv.url } ?: "",
                title = withContext(Dispatchers.Main) { wv.title } ?: "",
                pageState = withContext(Dispatchers.Main) { wv.pageLifecycleState },
                elementCount = treeArr?.length() ?: 0,
                truncated = fullCaptureObj.optBoolean("truncated", false),
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
            if (result is AgentResult.Error && result.error is AgentError.NoNavigationHistory) return result
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
            is AgentAction.GoBack -> {
                if (!withContext(Dispatchers.Main) { wv.canGoBack() }) {
                    return AgentResult.Error(AgentError.NoNavigationHistory("back"))
                }
                val urlBefore = withContext(Dispatchers.Main) { wv.url }
                withContext(Dispatchers.Main) { wv.goBack() }
                waitForPageSettlement(wv, urlBefore)
            }
            is AgentAction.GoForward -> {
                if (!withContext(Dispatchers.Main) { wv.canGoForward() }) {
                    return AgentResult.Error(AgentError.NoNavigationHistory("forward"))
                }
                val urlBefore = withContext(Dispatchers.Main) { wv.url }
                withContext(Dispatchers.Main) { wv.goForward() }
                waitForPageSettlement(wv, urlBefore)
            }
            is AgentAction.Refresh -> {
                val urlBefore = withContext(Dispatchers.Main) { wv.url }
                withContext(Dispatchers.Main) { wv.reload() }
                waitForPageSettlement(wv, urlBefore)
            }
            is AgentAction.Wait -> { delay(action.durationMs); AgentResult.Success(Unit) }
            is AgentAction.SendKeys -> handleSendKeys(action.keys)
            is AgentAction.ScrollToPercent -> handleScrollToPercent(action.yPercent, action.agentId)
            is AgentAction.ScrollToText -> handleScrollToText(action.text, action.nth)
            is AgentAction.ScrollToTop -> handleScrollToTop(action.agentId)
            is AgentAction.ScrollToBottom -> handleScrollToBottom(action.agentId)
            is AgentAction.PreviousPage -> handlePreviousPage(action.agentId)
            is AgentAction.NextPage -> handleNextPage(action.agentId)
            is AgentAction.GetDropdownOptions -> handleGetDropdownOptions(action.agentId)
            is AgentAction.SelectDropdownOption -> handleSelectDropdownOption(action.agentId, action.text)
            is AgentAction.Done -> AgentResult.Success(Unit)
        }
    }

    // ─── Action Handlers ──────────────────────────────────────────────

    private suspend fun handleClick(agentId: String): AgentResult<Unit> {
        val isFile = evalJsBool("__AgenticInternal.isFileUploader('$agentId')")
        if (isFile) return AgentResult.Error(AgentError.FileUploaderDetected(agentId))

        if (occludedMap[agentId] == true) {
            logger.w("Controller", "Element $agentId is occluded, proceeding with click anyway")
        }

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
        evalJsBool("__AgenticInternal.waitForStability('$agentId', ${config.elementStabilityTimeoutMs})")
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

    private suspend fun handleScrollToTop(agentId: String?): AgentResult<Unit> {
        val agentIdArg = if (agentId != null) "'$agentId'" else "undefined"
        evalJsVoid("__AgenticInternal.scrollToTop($agentIdArg)")
        return AgentResult.Success(Unit)
    }

    private suspend fun handleScrollToBottom(agentId: String?): AgentResult<Unit> {
        val agentIdArg = if (agentId != null) "'$agentId'" else "undefined"
        evalJsVoid("__AgenticInternal.scrollToBottom($agentIdArg)")
        return AgentResult.Success(Unit)
    }

    private suspend fun handlePreviousPage(agentId: String?): AgentResult<Unit> {
        val agentIdArg = if (agentId != null) "'$agentId'" else "undefined"
        evalJsVoid("__AgenticInternal.previousPage($agentIdArg)")
        return AgentResult.Success(Unit)
    }

    private suspend fun handleNextPage(agentId: String?): AgentResult<Unit> {
        val agentIdArg = if (agentId != null) "'$agentId'" else "undefined"
        evalJsVoid("__AgenticInternal.nextPage($agentIdArg)")
        return AgentResult.Success(Unit)
    }

    private suspend fun handleGetDropdownOptions(agentId: String): AgentResult<Unit> {
        val result = evalJsRaw("__AgenticInternal.getDropdownOptions('$agentId')")
        val json = when (result) {
            is AgentResult.Success -> result.data.ifBlank { "[]" }
            is AgentResult.Error -> "[]"
        }
        lastDropdownOptions = json
        logger.d("Controller", "Dropdown options: $lastDropdownOptions")
        return AgentResult.Success(Unit)
    }

    suspend fun canGoBack(): Boolean = withContext(Dispatchers.Main) { webView?.canGoBack() == true }

    suspend fun canGoForward(): Boolean = withContext(Dispatchers.Main) { webView?.canGoForward() == true }

    suspend fun getDropdownOptions(agentId: String): AgentResult<List<DropdownOption>> = mutex.withLock {
        return try {
            val json = when (val result = evalJsRaw("__AgenticInternal.getDropdownOptions('$agentId')")) {
                is AgentResult.Success -> result.data.ifBlank { "[]" }
                is AgentResult.Error -> "[]"
            }
            lastDropdownOptions = json
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

    private suspend fun waitForPageSettlement(wv: AgenticWebView, urlForError: String?): AgentResult<Unit> {
        val settled = withTimeoutOrNull(config.pageSettleTimeoutMs) {
            while (true) {
                val state = withContext(Dispatchers.Main) { wv.pageLifecycleState }
                if (state == PageLifecycleState.COMPLETE) break
                if (state == PageLifecycleState.ERROR) {
                    val httpCode = withContext(Dispatchers.Main) { wv.lastNavigationHttpError }
                    return@withTimeoutOrNull AgentResult.Error(
                        AgentError.NavigationFailed(urlForError ?: wv.url ?: "", httpCode)
                    )
                }
                delay(100)
            }
            AgentResult.Success(Unit)
        }
        if (settled != null) return settled

        val httpCode = withContext(Dispatchers.Main) { wv.lastNavigationHttpError }
        return if (httpCode != null && httpCode >= 400) {
            AgentResult.Error(AgentError.NavigationFailed(urlForError ?: wv.url ?: "", httpCode))
        } else {
            AgentResult.Error(AgentError.Timeout("Navigation", config.pageSettleTimeoutMs))
        }
    }

    private suspend fun handleNavigate(url: String): AgentResult<Unit> {
        val wv = webView ?: return AgentResult.Error(AgentError.PageNotReady(PageLifecycleState.IDLE))
        withContext(Dispatchers.Main) { wv.loadUrl(url) }
        return waitForPageSettlement(wv, url)
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

    // ─── Screenshot (delegated to ScreenshotCapture) ────────────────

    private suspend fun captureScreenshot(): AgentResult<String> =
        screenshotCapture.capture()

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
        screenshotCapture.destroy()
        cancelAllPendingPromises("Controller destroyed")
    }

    fun pauseTimers() { webView?.pauseTimers() }
    fun resumeTimers() { webView?.resumeTimers() }
}
