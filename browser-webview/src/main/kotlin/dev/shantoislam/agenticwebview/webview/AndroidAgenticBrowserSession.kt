package dev.shantoislam.agenticwebview.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.net.http.SslError
import android.webkit.PermissionRequest
import android.webkit.GeolocationPermissions
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.MotionEvent
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.shantoislam.agenticwebview.api.AgenticBrowserConfiguration
import dev.shantoislam.agenticwebview.api.AgenticBrowserSession
import dev.shantoislam.agenticwebview.api.BrowserCommand
import dev.shantoislam.agenticwebview.api.BrowserDiagnosticEvent
import dev.shantoislam.agenticwebview.api.BrowserDiagnosticsSink
import dev.shantoislam.agenticwebview.api.BrowserError
import dev.shantoislam.agenticwebview.api.BrowserEvent
import dev.shantoislam.agenticwebview.api.BrowserObservation
import dev.shantoislam.agenticwebview.api.BrowserResult
import dev.shantoislam.agenticwebview.api.BrowserScreenshotProvider
import dev.shantoislam.agenticwebview.api.BrowserSessionPhase
import dev.shantoislam.agenticwebview.api.BrowserSessionState
import dev.shantoislam.agenticwebview.api.CommandReceipt
import dev.shantoislam.agenticwebview.api.DialogType
import dev.shantoislam.agenticwebview.api.DocumentId
import dev.shantoislam.agenticwebview.api.HistoryNavigationRequest
import dev.shantoislam.agenticwebview.api.NavigationOperation
import dev.shantoislam.agenticwebview.api.NavigationReceipt
import dev.shantoislam.agenticwebview.api.NavigationRequest
import dev.shantoislam.agenticwebview.api.ObservationOptions
import dev.shantoislam.agenticwebview.api.ObservationRevision
import dev.shantoislam.agenticwebview.api.ObservationWarning
import dev.shantoislam.agenticwebview.api.PointerActionStrategy
import dev.shantoislam.agenticwebview.api.PointerButton
import dev.shantoislam.agenticwebview.api.PrivilegedRequestPolicy
import dev.shantoislam.agenticwebview.api.WaitCondition
import dev.shantoislam.agenticwebview.api.WaitReceipt
import dev.shantoislam.agenticwebview.webview.protocol.GatewayResult
import dev.shantoislam.agenticwebview.webview.protocol.RuntimeMethods
import dev.shantoislam.agenticwebview.webview.protocol.RuntimeProtocolBridge
import dev.shantoislam.agenticwebview.webview.protocol.RuntimeProtocolGateway
import dev.shantoislam.agenticwebview.webview.protocol.RuntimeResponseStatus
import dev.shantoislam.agenticwebview.webview.protocol.WebViewRuntimeTransport
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@SuppressLint("SetJavaScriptEnabled")
internal class AndroidAgenticBrowserSession(
    private val webView: WebView,
    private val configuration: AgenticBrowserConfiguration,
    private val ownsWebView: Boolean,
    private val delegate: BrowserHostDelegate,
    private val diagnosticsSink: BrowserDiagnosticsSink?,
    screenshotProvider: BrowserScreenshotProvider?,
) : AgenticBrowserSession {
    private val previousWebViewClient = webView.webViewClient
    private val previousWebChromeClient = webView.webChromeClient
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.Main.immediate)
    private val closed = AtomicBoolean(false)
    private val sessionId = UUID.randomUUID().toString()
    private val bridgeToken = UUID.randomUUID().toString() + UUID.randomUUID().toString()
    private var activeDocumentId: DocumentId? = null
    private var lastHttpStatus: Int? = null
    private var runtimeScript: String? = null
    private var documentStartScriptHandler: ScriptHandler? = null
    private var lastRendererDidCrash = false
    @Volatile private var navigationAborted = false
    private val interactionMutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
        classDiscriminator = "type"
    }
    private val screenshotProvider: BrowserScreenshotProvider = screenshotProvider
        ?: PixelCopyScreenshotProvider(
            webViewProvider = { if (closed.get()) null else webView },
            configuration = configuration.screenshots,
        )

    private val _state = MutableStateFlow(BrowserSessionState(BrowserSessionPhase.DETACHED))
    override val state: StateFlow<BrowserSessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BrowserEvent>(extraBufferCapacity = 64)
    override val events: Flow<BrowserEvent> = _events.asSharedFlow()

    private lateinit var gateway: RuntimeProtocolGateway
    private val bridge = RuntimeProtocolBridge(
        scope = scope,
        gatewayProvider = { if (::gateway.isInitialized) gateway else null },
        maximumMessageBytes = configuration.runtime.maximumMessageBytes,
        maximumConcurrentCallbacks = configuration.runtime.maximumPendingRequests * 2,
        onRejectedResponse = { emitDiagnostic("Rejected runtime response", mapOf("reason" to it)) },
        transportErrorHandler = { emitDiagnostic("Runtime transport error", mapOf("reason" to it)) },
    )

    init {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "AndroidAgenticBrowserSession must be created on the Android main thread"
        }
        configuration.requireValid()
        configureWebView()
        installDocumentStartRuntime()
        gateway = RuntimeProtocolGateway(
            bridgeToken = bridgeToken,
            maximumPendingRequests = configuration.runtime.maximumPendingRequests,
            maximumMessageBytes = configuration.runtime.maximumMessageBytes,
            requestTimeoutMs = configuration.runtime.requestTimeoutMs,
            transport = WebViewRuntimeTransport { if (closed.get()) null else webView },
        )
        webView.addJavascriptInterface(bridge, PROTOCOL_BRIDGE_NAME)
        installClients()
        transition(BrowserSessionState(BrowserSessionPhase.ATTACHED))
    }

    override suspend fun navigate(request: NavigationRequest): BrowserResult<NavigationReceipt> =
        diagnosticOperation("navigation.url") {
            interactionMutex.withLock { navigateLocked(request) }
        }

    private suspend fun navigateLocked(request: NavigationRequest): BrowserResult<NavigationReceipt> {
        if (closed.get()) return failure(BrowserError.SessionClosed())
        when (val decision = NavigationPolicyEvaluator.evaluate(request.url, configuration.navigation)) {
            NavigationDecision.Allow -> Unit
            is NavigationDecision.Block -> {
                _events.tryEmit(BrowserEvent.NavigationBlocked(request.url, decision.reason))
                safelyDelegate { onNavigationBlocked(request.url, decision.reason) }
                return failure(BrowserError.NavigationBlocked(request.url, decision.reason))
            }
        }

        return try {
            activeDocumentId?.let { documentId ->
                gateway.cancelDocument(documentId, "Navigation requested")
            }
            activeDocumentId = null
            withContext(Dispatchers.Main.immediate) {
                navigationAborted = false
                transition(
                    BrowserSessionState(
                        phase = BrowserSessionPhase.NAVIGATING,
                        url = request.url,
                    ),
                )
                webView.loadUrl(request.url)
            }
            val readyState = withTimeout(configuration.runtime.initializationTimeoutMs) {
                state.first {
                    it.phase == BrowserSessionPhase.READY ||
                        it.phase == BrowserSessionPhase.FAILED ||
                        it.phase == BrowserSessionPhase.RENDERER_TERMINATED ||
                        it.phase == BrowserSessionPhase.CLOSED
                }
            }
            when (readyState.phase) {
                BrowserSessionPhase.READY -> {
                    when (val waited = await(request.readiness)) {
                        is BrowserResult.Failure -> waited
                        is BrowserResult.Success -> BrowserResult.Success(
                            NavigationReceipt(
                                operation = NavigationOperation.URL,
                                requestedUrl = request.url,
                                finalUrl = readyState.url ?: request.url,
                                documentId = requireNotNull(readyState.documentId),
                                phase = readyState.phase,
                            ),
                        )
                    }
                }
                BrowserSessionPhase.RENDERER_TERMINATED -> failure(BrowserError.RendererTerminated(lastRendererDidCrash))
                BrowserSessionPhase.CLOSED -> failure(BrowserError.SessionClosed())
                else -> failure(BrowserError.NavigationFailed(request.url, lastHttpStatus))
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            abortNavigation("Navigation timed out")
            failure(BrowserError.Timeout("navigation", configuration.runtime.initializationTimeoutMs))
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                abortNavigation("Navigation caller was cancelled")
            }
            throw cancellation
        } catch (error: Exception) {
            abortNavigation("Navigation failed")
            failure(BrowserError.NavigationFailed(request.url, lastHttpStatus, error.message ?: "Navigation failed"))
        }
    }

    override suspend fun navigateHistory(
        request: HistoryNavigationRequest,
    ): BrowserResult<NavigationReceipt> = diagnosticOperation("navigation.${request.operation.name.lowercase()}") {
        interactionMutex.withLock { navigateHistoryLocked(request) }
    }

    private suspend fun navigateHistoryLocked(
        request: HistoryNavigationRequest,
    ): BrowserResult<NavigationReceipt> {
        if (closed.get()) return failure(BrowserError.SessionClosed())

        val historyAvailable = withContext(Dispatchers.Main.immediate) {
            when (request.operation) {
                NavigationOperation.BACK -> webView.canGoBack()
                NavigationOperation.FORWARD -> webView.canGoForward()
                NavigationOperation.RELOAD -> webView.url != null
                NavigationOperation.URL -> false
            }
        }
        if (!historyAvailable) {
            return failure(BrowserError.NavigationHistoryUnavailable(request.operation))
        }

        return try {
            activeDocumentId?.let { documentId ->
                gateway.cancelDocument(documentId, "History navigation requested")
            }
            activeDocumentId = null
            withContext(Dispatchers.Main.immediate) {
                navigationAborted = false
                transition(
                    BrowserSessionState(
                        phase = BrowserSessionPhase.NAVIGATING,
                        url = webView.url,
                    ),
                )
                when (request.operation) {
                    NavigationOperation.BACK -> webView.goBack()
                    NavigationOperation.FORWARD -> webView.goForward()
                    NavigationOperation.RELOAD -> webView.reload()
                    NavigationOperation.URL -> error("URL navigation cannot be used as a history operation")
                }
            }
            val readyState = withTimeout(configuration.runtime.initializationTimeoutMs) {
                state.first {
                    it.phase == BrowserSessionPhase.READY ||
                        it.phase == BrowserSessionPhase.FAILED ||
                        it.phase == BrowserSessionPhase.RENDERER_TERMINATED ||
                        it.phase == BrowserSessionPhase.CLOSED
                }
            }
            when (readyState.phase) {
                BrowserSessionPhase.READY -> when (val waited = await(request.readiness)) {
                    is BrowserResult.Failure -> waited
                    is BrowserResult.Success -> BrowserResult.Success(
                        NavigationReceipt(
                            operation = request.operation,
                            finalUrl = readyState.url.orEmpty(),
                            documentId = requireNotNull(readyState.documentId),
                            phase = readyState.phase,
                        ),
                    )
                }
                BrowserSessionPhase.RENDERER_TERMINATED -> failure(BrowserError.RendererTerminated(lastRendererDidCrash))
                BrowserSessionPhase.CLOSED -> failure(BrowserError.SessionClosed())
                else -> failure(BrowserError.NavigationFailed(readyState.url.orEmpty(), lastHttpStatus))
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            abortNavigation("History navigation timed out")
            failure(BrowserError.Timeout("history navigation", configuration.runtime.initializationTimeoutMs))
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                abortNavigation("History navigation caller was cancelled")
            }
            throw cancellation
        } catch (error: Exception) {
            abortNavigation("History navigation failed")
            failure(
                BrowserError.NavigationFailed(
                    _state.value.url.orEmpty(),
                    lastHttpStatus,
                    error.message ?: "History navigation failed",
                ),
            )
        }
    }

    private suspend fun abortNavigation(reason: String) {
        navigationAborted = true
        activeDocumentId?.let { gateway.cancelDocument(it, reason) }
        activeDocumentId = null
        withContext(Dispatchers.Main.immediate) {
            webView.stopLoading()
            transition(_state.value.copy(phase = BrowserSessionPhase.FAILED, documentId = null))
        }
    }

    override suspend fun observe(options: ObservationOptions): BrowserResult<BrowserObservation> =
        diagnosticOperation("observation.capture") { observeInternal(options) }

    private suspend fun observeInternal(options: ObservationOptions): BrowserResult<BrowserObservation> {
        val documentId = activeDocumentId
            ?: return failure(BrowserError.PageNotReady(_state.value.phase))
        if (closed.get()) return failure(BrowserError.SessionClosed())
        if (_state.value.phase !in OBSERVABLE_PHASES) {
            return failure(BrowserError.PageNotReady(_state.value.phase))
        }

        val observationConfig = configuration.observation
        val payload = buildJsonObject {
            put("maximumVisitedNodes", observationConfig.maximumVisitedNodes)
            put("maximumEmittedNodes", observationConfig.maximumEmittedNodes)
            put("maximumTotalTextCharacters", observationConfig.maximumTotalTextCharacters)
            put("maximumTextCharactersPerNode", observationConfig.maximumTextCharactersPerNode)
            put("maximumTraversalMs", observationConfig.maximumTraversalMs)
            put("maximumFrameDepth", observationConfig.maximumFrameDepth)
            put("maximumShadowDepth", observationConfig.maximumShadowDepth)
            put("viewportExpansionPx", if (options.includeOffscreenContent) -1 else observationConfig.viewportExpansionPx)
            put("includeCompactText", options.includeCompactText)
        }
        return when (val response = gateway.request(sessionId, documentId, RuntimeMethods.CAPTURE_OBSERVATION, payload)) {
            is GatewayResult.Response -> decodeObservation(response, options)
            else -> gatewayFailure(response, "observation.capture")
        }
    }

    override suspend fun execute(command: BrowserCommand): BrowserResult<CommandReceipt> =
        diagnosticOperation("action.${command::class.simpleName?.lowercase() ?: "unknown"}") {
            interactionMutex.withLock { executeLocked(command) }
        }

    private suspend fun executeLocked(command: BrowserCommand): BrowserResult<CommandReceipt> {
        val documentId = activeDocumentId
            ?: return failure(BrowserError.PageNotReady(_state.value.phase))
        if (closed.get()) return failure(BrowserError.SessionClosed())
        if (_state.value.phase !in OBSERVABLE_PHASES) {
            return failure(BrowserError.PageNotReady(_state.value.phase))
        }
        val revisionBefore = _state.value.revision

        if (command is BrowserCommand.Click &&
            command.button == PointerButton.PRIMARY &&
            configuration.actions.pointerStrategy == PointerActionStrategy.NATIVE_PREFERRED
        ) {
            return executeNativeClick(documentId, command)
        }

        val payload = buildJsonObject {
            put("command", json.encodeToJsonElement(BrowserCommand.serializer(), command))
            put("options", actionOptionsPayload())
        }
        val actionTimeoutMs = configuration.actions.preparationTimeoutMs +
            configuration.actions.verificationTimeoutMs +
            ((command as? BrowserCommand.LongPress)?.durationMs ?: 0L)
        return when (val response = gateway.request(
            sessionId,
            documentId,
            RuntimeMethods.EXECUTE_ACTION,
            payload,
            timeoutMs = actionTimeoutMs,
        )) {
            is GatewayResult.Response -> decodeCommandReceipt(command, response)
            else -> if (activeDocumentId != documentId) {
                BrowserResult.Success(
                    CommandReceipt(
                        commandType = command.stableTypeName(),
                        strategy = command.defaultExecutionStrategy(),
                        documentId = documentId,
                        revisionBefore = revisionBefore,
                        revisionAfter = revisionBefore,
                        dispatched = true,
                        verified = true,
                        pageChanged = true,
                    ),
                )
            } else {
                gatewayFailure(response, "action.execute")
            }
        }
    }

    private suspend fun executeNativeClick(
        documentId: DocumentId,
        command: BrowserCommand.Click,
    ): BrowserResult<CommandReceipt> {
        val preparationPayload = buildJsonObject {
            put("target", json.encodeToJsonElement(command.target))
            put("options", actionOptionsPayload())
        }
        val preparation = when (val response = gateway.request(
            sessionId,
            documentId,
            RuntimeMethods.PREPARE_NATIVE_CLICK,
            preparationPayload,
        )) {
            is GatewayResult.Response -> {
                if (response.envelope.status == RuntimeResponseStatus.ERROR) {
                    return actionRuntimeFailure(command, response.envelope.error)
                }
                try {
                    json.decodeFromJsonElement<NativeClickPreparation>(response.envelope.result)
                } catch (error: Exception) {
                    return failure(BrowserError.MalformedRuntimeResponse(error.message ?: "Native click preparation is malformed"))
                }
            }
            else -> return gatewayFailure(response, "action.prepare_native_click")
        }
        if (preparation.viewportWidthCssPx <= 0.0 || preparation.viewportHeightCssPx <= 0.0) {
            return failure(BrowserError.MalformedRuntimeResponse("Native click preparation has invalid viewport geometry"))
        }

        withContext(Dispatchers.Main.immediate) {
            val x = (preparation.xCssPx / preparation.viewportWidthCssPx * webView.width)
                .toFloat()
                .coerceIn(0f, (webView.width - 1).coerceAtLeast(0).toFloat())
            val y = (preparation.yCssPx / preparation.viewportHeightCssPx * webView.height)
                .toFloat()
                .coerceIn(0f, (webView.height - 1).coerceAtLeast(0).toFloat())
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            try {
                webView.dispatchTouchEvent(down)
                webView.dispatchTouchEvent(up)
            } finally {
                down.recycle()
                up.recycle()
            }
        }

        val verificationPayload = buildJsonObject { put("token", preparation.token) }
        return when (val response = gateway.request(
            sessionId,
            documentId,
            RuntimeMethods.VERIFY_NATIVE_CLICK,
            verificationPayload,
        )) {
            is GatewayResult.Response -> decodeCommandReceipt(command, response)
            else -> if (activeDocumentId != documentId) {
                BrowserResult.Success(
                    CommandReceipt(
                        commandType = "click",
                        strategy = dev.shantoislam.agenticwebview.api.ActionExecutionStrategy.ANDROID_NATIVE_POINTER,
                        documentId = documentId,
                        revisionBefore = preparation.revisionBefore,
                        revisionAfter = preparation.revisionBefore,
                        dispatched = true,
                        verified = true,
                        pageChanged = true,
                    ),
                )
            } else {
                gatewayFailure(response, "action.verify_native_click")
            }
        }
    }

    private fun actionOptionsPayload() = buildJsonObject {
        put("geometryStableCycles", configuration.actions.geometryStableCycles)
        put("geometryTolerancePx", configuration.actions.geometryTolerancePx)
    }

    override suspend fun await(condition: WaitCondition): BrowserResult<WaitReceipt> =
        diagnosticOperation("wait.${condition::class.simpleName?.lowercase() ?: "condition"}") {
            awaitInternal(condition)
        }

    private suspend fun awaitInternal(condition: WaitCondition): BrowserResult<WaitReceipt> {
        val documentId = activeDocumentId
            ?: return failure(BrowserError.PageNotReady(_state.value.phase))
        if (closed.get()) return failure(BrowserError.SessionClosed())

        val satisfied = when (condition) {
            WaitCondition.PageReady -> withTimeoutOrNull(configuration.runtime.requestTimeoutMs) {
                state.first { it.phase == BrowserSessionPhase.READY }
                true
            } ?: false
            is WaitCondition.DomQuiet -> awaitDomQuiet(condition.quietWindowMs)
            is WaitCondition.ElementPresent -> {
                withTimeoutOrNull(configuration.runtime.requestTimeoutMs) {
                    while (activeDocumentId == documentId && !closed.get()) {
                        when (val observation = observe(ObservationOptions(includeCompactText = false))) {
                            is BrowserResult.Success -> if (
                                observation.value.nodes.any { it.elementRef?.let { ref ->
                                    ref.documentId == condition.target.documentId &&
                                        ref.frameId == condition.target.frameId &&
                                        ref.elementId == condition.target.elementId
                                } == true }
                            ) return@withTimeoutOrNull true
                            is BrowserResult.Failure -> Unit
                        }
                        delay(ELEMENT_POLL_INTERVAL_MS)
                    }
                    false
                } ?: false
            }
        }
        return if (satisfied) {
            BrowserResult.Success(
                WaitReceipt(
                    conditionType = condition::class.simpleName ?: "condition",
                    satisfiedAtEpochMs = System.currentTimeMillis(),
                    documentId = documentId,
                    revision = _state.value.revision,
                ),
            )
        } else {
            failure(BrowserError.Timeout("wait condition", configuration.runtime.requestTimeoutMs))
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        transition(_state.value.copy(phase = BrowserSessionPhase.CLOSED))
        scope.launch {
            try {
                activeDocumentId?.let { gateway.cancelDocument(it, "Session closed") }
                gateway.close("Session closed")
                webView.removeJavascriptInterface(PROTOCOL_BRIDGE_NAME)
                screenshotProvider.close()
                documentStartScriptHandler?.remove()
                documentStartScriptHandler = null
                webView.stopLoading()
                if (ownsWebView) {
                    webView.webViewClient = WebViewClient()
                    webView.webChromeClient = WebChromeClient()
                } else {
                    webView.webViewClient = previousWebViewClient
                    webView.webChromeClient = previousWebChromeClient
                }
                webView.setDownloadListener(null)
                if (ownsWebView) webView.destroy()
            } finally {
                scope.cancel()
            }
        }
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setSupportMultipleWindows(true)
        }
    }

    private fun installDocumentStartRuntime() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val script = loadRuntimeScript() ?: return
        try {
            documentStartScriptHandler = WebViewCompat.addDocumentStartJavaScript(
                webView,
                runtimeInstallationSource(script),
                setOf("*"),
            )
        } catch (error: Exception) {
            emitDiagnostic(
                "Document-start runtime installation failed",
                mapOf("reason" to (error.message ?: error::class.java.simpleName)),
            )
        }
    }

    private fun installClients() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (closed.get() || navigationAborted) return true
                return when (val decision = NavigationPolicyEvaluator.evaluate(request.url.toString(), configuration.navigation)) {
                    NavigationDecision.Allow -> false
                    is NavigationDecision.Block -> {
                        val blockedUrl = request.url.toString()
                        if (request.isForMainFrame) {
                            transition(_state.value.copy(phase = BrowserSessionPhase.FAILED))
                            emitDiagnostic("Blocked navigation", mapOf("reason" to decision.reason))
                        }
                        _events.tryEmit(BrowserEvent.NavigationBlocked(blockedUrl, decision.reason))
                        safelyDelegate { onNavigationBlocked(blockedUrl, decision.reason) }
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (closed.get() || navigationAborted) {
                    view.stopLoading()
                    return
                }
                val previousDocument = activeDocumentId
                val documentId = DocumentId(UUID.randomUUID().toString())
                activeDocumentId = documentId
                lastHttpStatus = null
                transition(
                    BrowserSessionState(
                        phase = BrowserSessionPhase.DOCUMENT_CREATED,
                        documentId = documentId,
                        revision = ObservationRevision(0),
                        url = url,
                    ),
                )
                previousDocument?.let { old ->
                    scope.launch { gateway.cancelDocument(old, "Main document navigation started") }
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (closed.get() || navigationAborted) return
                val documentId = activeDocumentId ?: return
                if (_state.value.documentId != documentId || _state.value.phase != BrowserSessionPhase.DOCUMENT_CREATED) {
                    return
                }
                if (url != null && _state.value.url != null && url != _state.value.url) return
                scope.launch { initializeRuntime(documentId, url) }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    transition(_state.value.copy(phase = BrowserSessionPhase.FAILED))
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) {
                    lastHttpStatus = response.statusCode
                    if (response.statusCode >= 400) {
                        transition(_state.value.copy(phase = BrowserSessionPhase.FAILED))
                    }
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (closed.get()) return true
                lastRendererDidCrash = detail.didCrash()
                transition(_state.value.copy(phase = BrowserSessionPhase.RENDERER_TERMINATED))
                _events.tryEmit(BrowserEvent.RendererTerminated(lastRendererDidCrash))
                activeDocumentId?.let { documentId ->
                    scope.launch { gateway.cancelDocument(documentId, "Renderer terminated") }
                }
                return true
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                transition(_state.value.copy(phase = BrowserSessionPhase.FAILED))
                val url = error.url.orEmpty()
                _events.tryEmit(BrowserEvent.SslErrorReceived(url, error.primaryError))
                safelyDelegate { onSslError(url, error.primaryError) }
                emitDiagnostic("SSL error blocked", mapOf("url" to url, "primaryError" to error.primaryError.toString()))
            }

            override fun onSafeBrowsingHit(
                view: WebView,
                request: WebResourceRequest,
                threatType: Int,
                callback: SafeBrowsingResponse,
            ) {
                callback.backToSafety(true)
                if (request.isForMainFrame) transition(_state.value.copy(phase = BrowserSessionPhase.FAILED))
                val url = request.url.toString()
                _events.tryEmit(BrowserEvent.SafeBrowsingHit(url, threatType))
                safelyDelegate { onSafeBrowsingHit(url, threatType) }
                emitDiagnostic("Safe Browsing threat blocked", mapOf("url" to url, "threatType" to threatType.toString()))
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean =
                handleDialog(DialogType.ALERT, message, null, result)

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean =
                handleDialog(DialogType.CONFIRM, message, null, result)

            override fun onJsPrompt(
                view: WebView,
                url: String,
                message: String,
                defaultValue: String?,
                result: JsPromptResult,
            ): Boolean = handleDialog(DialogType.PROMPT, message, defaultValue, result)

            override fun onJsBeforeUnload(
                view: WebView,
                url: String,
                message: String,
                result: JsResult,
            ): Boolean = handleDialog(DialogType.BEFORE_UNLOAD, message, null, result)

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                if (closed.get()) return false
                val urlHint = view.hitTestResult?.extra
                _events.tryEmit(BrowserEvent.PopupRequested(urlHint, isUserGesture))
                val decision = when (configuration.navigation.popupPolicy) {
                    PrivilegedRequestPolicy.ALLOW -> BrowserPopupDecision.OPEN_IN_CURRENT_SESSION
                    PrivilegedRequestPolicy.DENY -> BrowserPopupDecision.DENY
                    PrivilegedRequestPolicy.ASK -> safelyDelegate(BrowserPopupDecision.DENY) {
                        onPopup(BrowserPopupRequest(urlHint, isUserGesture))
                    }
                }
                return decision == BrowserPopupDecision.OPEN_IN_CURRENT_SESSION &&
                    openPopupInCurrentSession(view, resultMsg)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                if (closed.get()) {
                    request.deny()
                    return
                }
                val resources = request.resources.orEmpty().toSet()
                val origin = request.origin?.toString().orEmpty()
                _events.tryEmit(BrowserEvent.PermissionRequested(origin, resources.sorted()))
                val approved = when (configuration.navigation.permissionPolicy) {
                    PrivilegedRequestPolicy.ALLOW -> resources
                    PrivilegedRequestPolicy.DENY -> emptySet()
                    PrivilegedRequestPolicy.ASK -> safelyDelegate(emptySet()) {
                        onPermission(BrowserPermissionRequest(origin, resources))
                    }
                }.intersect(resources)
                if (approved.isEmpty()) request.deny() else request.grant(approved.toTypedArray())
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback,
            ) {
                if (closed.get()) {
                    callback.invoke(origin, false, false)
                    return
                }
                val resources = setOf(GEOLOCATION_RESOURCE)
                _events.tryEmit(BrowserEvent.PermissionRequested(origin, resources.toList()))
                val approved = when (configuration.navigation.permissionPolicy) {
                    PrivilegedRequestPolicy.ALLOW -> resources
                    PrivilegedRequestPolicy.DENY -> emptySet()
                    PrivilegedRequestPolicy.ASK -> safelyDelegate(emptySet()) {
                        onPermission(BrowserPermissionRequest(origin, resources))
                    }
                }.contains(GEOLOCATION_RESOURCE)
                callback.invoke(origin, approved, false)
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                if (closed.get()) {
                    filePathCallback.onReceiveValue(null)
                    return true
                }
                val request = BrowserFileChooserRequest(
                    acceptTypes = fileChooserParams.acceptTypes.orEmpty().filter { it.isNotBlank() },
                    captureEnabled = fileChooserParams.isCaptureEnabled,
                    allowsMultiple = fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE,
                )
                if (configuration.navigation.fileChooserPolicy == PrivilegedRequestPolicy.ASK) {
                    _events.tryEmit(BrowserEvent.FileChooserRequested(request.acceptTypes, request.captureEnabled))
                }
                if (configuration.navigation.fileChooserPolicy == PrivilegedRequestPolicy.DENY) {
                    filePathCallback.onReceiveValue(null)
                    return true
                }
                val handled = safelyDelegate(false) {
                    onFileChooser(request, filePathCallback::onReceiveValue)
                }
                if (!handled) filePathCallback.onReceiveValue(null)
                return true
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            if (closed.get()) return@setDownloadListener
            val request = BrowserDownloadRequest(url, userAgent, contentDisposition, mimeType, contentLength)
            _events.tryEmit(BrowserEvent.DownloadRequested(url, mimeType, contentLength.takeIf { it >= 0 }))
            when (configuration.navigation.downloadPolicy) {
                PrivilegedRequestPolicy.ALLOW -> safelyDelegate { onDownload(request) }
                PrivilegedRequestPolicy.DENY -> Unit
                PrivilegedRequestPolicy.ASK -> safelyDelegate { onDownload(request) }
            }
        }
    }

    private fun openPopupInCurrentSession(source: WebView, resultMessage: Message): Boolean {
        val transport = resultMessage.obj as? WebView.WebViewTransport ?: return false
        val relay = WebView(source.context)
        val relayClosed = AtomicBoolean(false)
        val closeRelay = {
            if (relayClosed.compareAndSet(false, true)) {
                relay.stopLoading()
                relay.destroy()
            }
        }
        relay.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setSupportMultipleWindows(false)
        }
        relay.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                when (val decision = NavigationPolicyEvaluator.evaluate(url, configuration.navigation)) {
                    NavigationDecision.Allow -> source.loadUrl(url)
                    is NavigationDecision.Block -> {
                        _events.tryEmit(BrowserEvent.NavigationBlocked(url, decision.reason))
                        safelyDelegate { onNavigationBlocked(url, decision.reason) }
                    }
                }
                closeRelay()
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) closeRelay()
            }
        }
        return try {
            transport.webView = relay
            resultMessage.sendToTarget()
            scope.launch {
                delay(POPUP_RELAY_TIMEOUT_MS)
                closeRelay()
            }
            true
        } catch (error: Exception) {
            closeRelay()
            emitDiagnostic(
                "Popup relay failed",
                mapOf("reason" to (error.message ?: error::class.java.simpleName)),
            )
            false
        }
    }

    private suspend fun initializeRuntime(documentId: DocumentId, url: String?) {
        if (closed.get() || activeDocumentId != documentId) return
        transition(_state.value.copy(phase = BrowserSessionPhase.RUNTIME_INITIALIZING, url = url))
        val injectionFailure = injectRuntime()
        if (injectionFailure != null) {
            transition(_state.value.copy(phase = BrowserSessionPhase.FAILED))
            emitDiagnostic("Runtime injection failed", mapOf("reason" to injectionFailure))
            return
        }

        val configurePayload = buildJsonObject {
            put("runtime", runtimePayload())
            put("experimental", experimentalPayload())
        }
        val configureResult = gateway.request(
            sessionId,
            documentId,
            RuntimeMethods.CONFIGURE,
            configurePayload,
        )
        if (configureResult !is GatewayResult.Response ||
            configureResult.envelope.status != RuntimeResponseStatus.SUCCESS
        ) {
            transition(_state.value.copy(phase = BrowserSessionPhase.FAILED))
            emitDiagnostic("Runtime configuration failed", mapOf("result" to configureResult.safeDiagnosticSummary()))
            return
        }

        when (val ping = gateway.request(sessionId, documentId, RuntimeMethods.PING)) {
            is GatewayResult.Response -> {
                if (ping.envelope.status == RuntimeResponseStatus.SUCCESS && activeDocumentId == documentId) {
                    transition(_state.value.copy(phase = BrowserSessionPhase.INTERACTIVE, url = url))
                    transition(_state.value.copy(phase = BrowserSessionPhase.STABILIZING, url = url))
                    delay(configuration.runtime.readyQuietWindowMs)
                    if (activeDocumentId == documentId && !closed.get()) {
                        transition(_state.value.copy(phase = BrowserSessionPhase.READY, url = url))
                    }
                } else {
                    transition(_state.value.copy(phase = BrowserSessionPhase.FAILED))
                }
            }
            else -> {
                transition(_state.value.copy(phase = BrowserSessionPhase.FAILED))
                emitDiagnostic("Runtime handshake failed", mapOf("result" to ping.safeDiagnosticSummary()))
            }
        }
    }

    private suspend fun injectRuntime(): String? {
        val script = runtimeScript ?: withContext(Dispatchers.IO) { loadRuntimeScript() }
            ?: return "Runtime asset '$RUNTIME_ASSET_NAME' is unavailable"
        val installation = runtimeInstallationSource(script)
        val existingRuntimeCheck = if (documentStartScriptHandler != null) {
            """
                if (window.__AgenticWebRuntime &&
                    window.__AgenticWebRuntime.protocolVersion === 1 &&
                    typeof window.__AgenticWebRuntime.dispatchProtocol === 'function') {
                    return 'READY';
                }
            """.trimIndent()
        } else {
            """
                if (window.__AgenticWebRuntime) {
                    return 'ERROR:Page defined the reserved Agentic runtime global before installation';
                }
            """.trimIndent()
        }

        val wrapped = """
            (function() {
                try {
                    $existingRuntimeCheck
                    $installation
                    return window.__AgenticWebRuntime && typeof window.__AgenticWebRuntime.dispatchProtocol === 'function'
                        ? 'READY'
                        : 'MISSING_PROTOCOL';
                } catch (error) {
                    return 'ERROR:' + String(error && error.message ? error.message : error);
                }
            })();
        """.trimIndent()

        val result = withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<String?> { continuation ->
                webView.evaluateJavascript(wrapped) { raw ->
                    if (!continuation.isActive) return@evaluateJavascript
                    continuation.resume(decodeJavascriptString(raw))
                }
            }
        }
        return if (result == "READY") null else result ?: "Runtime injection returned no result"
    }

    private fun loadRuntimeScript(): String? {
        runtimeScript?.let { return it }
        return try {
            webView.context.assets.open(RUNTIME_ASSET_NAME).bufferedReader().use { it.readText() }
                .also { runtimeScript = it }
        } catch (_: Exception) {
            null
        }
    }

    private fun runtimeInstallationSource(script: String): String =
        "window.__AgenticWebRuntimeBootstrapConfiguration = " +
            buildJsonObject {
                put("runtime", runtimePayload())
                put("experimental", experimentalPayload())
            }.toString() +
            ";\n" + script

    private fun runtimePayload() = buildJsonObject {
        put("maximumMessageBytes", configuration.runtime.maximumMessageBytes)
    }

    private fun experimentalPayload() = buildJsonObject {
        val experimental = configuration.experimental
        put("hideWebDriverProperty", experimental.hideWebDriverProperty)
        put("installChromeLikeGlobals", experimental.installChromeLikeGlobals)
        put("forceFutureShadowRootsOpen", experimental.forceFutureShadowRootsOpen)
    }

    private suspend fun decodeObservation(
        response: GatewayResult.Response,
        options: ObservationOptions,
    ): BrowserResult<BrowserObservation> {
        val envelope = response.envelope
        if (envelope.status == RuntimeResponseStatus.ERROR) return runtimeFailure(envelope.error)
        return try {
            var observation = json.decodeFromJsonElement<BrowserObservation>(envelope.result)
            if (options.includeScreenshot) {
                observation = when (val screenshot = screenshotProvider.capture()) {
                    is BrowserResult.Success -> observation.copy(screenshot = screenshot.value)
                    is BrowserResult.Failure -> observation.copy(
                        warnings = observation.warnings + ObservationWarning(
                            code = "SCREENSHOT_FAILED",
                            message = screenshot.error.message,
                        ),
                    )
                }
            }
            if (observation.document.id != activeDocumentId) {
                failure(BrowserError.MalformedRuntimeResponse("Observation belongs to an inactive document"))
            } else {
                updateRevision(observation.document.id, observation.revision, observation.document.url)
                BrowserResult.Success(observation)
            }
        } catch (error: SerializationException) {
            failure(BrowserError.MalformedRuntimeResponse(error.message ?: "Observation response is malformed"))
        } catch (error: IllegalArgumentException) {
            failure(BrowserError.MalformedRuntimeResponse(error.message ?: "Observation response is invalid"))
        }
    }

    private fun decodeCommandReceipt(
        command: BrowserCommand,
        response: GatewayResult.Response,
    ): BrowserResult<CommandReceipt> {
        val envelope = response.envelope
        if (envelope.status == RuntimeResponseStatus.ERROR) return actionRuntimeFailure(command, envelope.error)
        return try {
            val receipt = json.decodeFromJsonElement<CommandReceipt>(envelope.result)
            if (receipt.documentId != activeDocumentId) {
                return failure(BrowserError.MalformedRuntimeResponse("Action receipt belongs to an inactive document"))
            }
            if (receipt.commandType != command.stableTypeName()) {
                return failure(BrowserError.MalformedRuntimeResponse("Action receipt command type does not match the request"))
            }
            updateRevision(receipt.documentId, receipt.revisionAfter)
            if (!receipt.dispatched) {
                failure(BrowserError.ActionRejected(command::class.simpleName ?: "command", "Runtime did not dispatch the command"))
            } else if (!receipt.verified) {
                failure(BrowserError.ActionNotVerified(command::class.simpleName ?: "command", receipt))
            } else {
                BrowserResult.Success(receipt)
            }
        } catch (error: Exception) {
            failure(BrowserError.MalformedRuntimeResponse(error.message ?: "Action response is malformed"))
        }
    }

    private suspend fun awaitDomQuiet(quietWindowMs: Long): Boolean {
        return withTimeoutOrNull(configuration.runtime.requestTimeoutMs) {
            while (true) {
                val before = when (val observation = observe(ObservationOptions(includeCompactText = false))) {
                    is BrowserResult.Success -> observation.value.revision
                    is BrowserResult.Failure -> return@withTimeoutOrNull false
                }
                delay(quietWindowMs)
                val after = when (val observation = observe(ObservationOptions(includeCompactText = false))) {
                    is BrowserResult.Success -> observation.value.revision
                    is BrowserResult.Failure -> return@withTimeoutOrNull false
                }
                if (before == after) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
    }

    private fun handleDialog(
        type: DialogType,
        message: String,
        defaultValue: String?,
        result: JsResult,
    ): Boolean {
        if (closed.get()) {
            result.cancel()
            return true
        }
        return when (configuration.navigation.dialogPolicy) {
            PrivilegedRequestPolicy.ALLOW -> {
                if (result is JsPromptResult) result.confirm(defaultValue ?: "") else result.confirm()
                true
            }
            PrivilegedRequestPolicy.DENY -> {
                result.cancel()
                true
            }
            PrivilegedRequestPolicy.ASK -> {
                _events.tryEmit(BrowserEvent.DialogRequested(type, message.take(2_000), defaultValue?.take(2_000)))
                when (val decision = safelyDelegate<BrowserDialogDecision>(BrowserDialogDecision.Cancel) {
                    onDialog(BrowserDialogRequest(type, message.take(2_000), defaultValue?.take(2_000)))
                }) {
                    is BrowserDialogDecision.Confirm -> {
                        if (result is JsPromptResult) {
                            result.confirm(decision.promptValue ?: defaultValue.orEmpty())
                        } else {
                            result.confirm()
                        }
                    }
                    BrowserDialogDecision.Cancel -> result.cancel()
                }
                true
            }
        }
    }

    private fun transition(next: BrowserSessionState) {
        val previous = _state.value
        when (val reduction = BrowserLifecycleReducer.reduce(previous, next)) {
            is LifecycleReduction.Accept -> {
                if (previous == reduction.state) return
                _state.value = reduction.state
                _events.tryEmit(BrowserEvent.StateChanged(previous, reduction.state))
            }
            is LifecycleReduction.Reject -> emitDiagnostic(
                "Rejected lifecycle transition",
                mapOf("reason" to reduction.reason),
            )
        }
    }

    private fun updateRevision(documentId: DocumentId, revision: ObservationRevision, url: String? = null) {
        val previousRevision = _state.value.revision
        transition(_state.value.copy(revision = revision, url = url ?: _state.value.url))
        if (revision != previousRevision) {
            _events.tryEmit(BrowserEvent.DocumentRevisionChanged(documentId, revision))
        }
    }

    private fun emitDiagnostic(
        message: String,
        attributes: Map<String, String>,
        operationId: String? = null,
        durationMs: Long? = null,
    ) {
        if (!configuration.diagnostics.enabled) return
        val safeAttributes = attributes.mapValues { (key, value) ->
            if (!configuration.diagnostics.includePageContent && SENSITIVE_DIAGNOSTIC_KEYS.any {
                key.contains(it, ignoreCase = true)
            }) {
                "[REDACTED]"
            } else {
                value.take(500)
            }
        }
        diagnosticsSink?.emit(
            BrowserDiagnosticEvent(
                category = message,
                operationId = operationId,
                documentId = activeDocumentId,
                revision = _state.value.revision,
                durationMs = durationMs,
                attributes = safeAttributes,
            ),
        )
        android.util.Log.d("AgenticWebView", "$message $safeAttributes")
    }

    private suspend fun <T> diagnosticOperation(category: String, block: suspend () -> T): T {
        if (!configuration.diagnostics.enabled) return block()
        val operationId = UUID.randomUUID().toString()
        val startedAt = android.os.SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            emitDiagnostic(
                message = category,
                attributes = emptyMap(),
                operationId = operationId,
                durationMs = android.os.SystemClock.elapsedRealtime() - startedAt,
            )
        }
    }

    private fun safelyDelegate(block: BrowserHostDelegate.() -> Unit) {
        try {
            delegate.block()
        } catch (error: Exception) {
            emitDiagnostic("Host delegate failure", mapOf("reason" to (error.message ?: error::class.java.simpleName)))
        }
    }

    private fun <T> safelyDelegate(fallback: T, block: BrowserHostDelegate.() -> T): T = try {
        delegate.block()
    } catch (error: Exception) {
        emitDiagnostic("Host delegate failure", mapOf("reason" to (error.message ?: error::class.java.simpleName)))
        fallback
    }

    private fun runtimeFailure(error: dev.shantoislam.agenticwebview.webview.protocol.RuntimeProtocolError?): BrowserResult.Failure {
        if (error == null) return failure(BrowserError.MalformedRuntimeResponse("Runtime error response has no error body"))
        val details = error.details.mapValues { (_, value) -> value.toString().take(1_000) }
        return failure(BrowserError.RuntimeFailure(error.code, error.message, details))
    }

    private fun actionRuntimeFailure(
        command: BrowserCommand,
        error: dev.shantoislam.agenticwebview.webview.protocol.RuntimeProtocolError?,
    ): BrowserResult.Failure {
        if (error == null) return failure(BrowserError.MalformedRuntimeResponse("Runtime error response has no error body"))
        val target = command.targetOrNull()
        return when (error.code) {
            "STALE_DOCUMENT", "STALE_ELEMENT" -> target
                ?.let { failure(BrowserError.StaleElementReference(it, error.message)) }
                ?: failure(BrowserError.RuntimeFailure(error.code, error.message))
            "ELEMENT_NOT_ACTIONABLE", "OPTION_NOT_FOUND" -> target
                ?.let { failure(BrowserError.ElementNotActionable(it, error.message)) }
                ?: failure(BrowserError.ActionRejected(command::class.simpleName ?: "command", error.message))
            "ELEMENT_OCCLUDED" -> target
                ?.let { failure(BrowserError.ElementOccluded(it, message = error.message)) }
                ?: failure(BrowserError.ActionRejected(command::class.simpleName ?: "command", error.message))
            "UNSUPPORTED_FRAME" -> target
                ?.let { failure(BrowserError.UnsupportedFrame(it.frameId, error.message)) }
                ?: failure(BrowserError.UnsupportedAction(command::class.simpleName ?: "command", error.message))
            "UNSUPPORTED_ACTION" -> failure(
                BrowserError.UnsupportedAction(command::class.simpleName ?: "command", error.message),
            )
            "INVALID_ACTION" -> failure(
                BrowserError.ActionRejected(command::class.simpleName ?: "command", error.message),
            )
            else -> runtimeFailure(error)
        }
    }

    private fun BrowserCommand.targetOrNull() = when (this) {
        is BrowserCommand.Click -> target
        is BrowserCommand.LongPress -> target
        is BrowserCommand.TypeText -> target
        is BrowserCommand.SelectOption -> target
        is BrowserCommand.ScrollIntoView -> target
        is BrowserCommand.Scroll -> (target as? dev.shantoislam.agenticwebview.api.ScrollTarget.Element)?.target
        is BrowserCommand.PressKeys -> null
    }

    private fun BrowserCommand.defaultExecutionStrategy() = when (this) {
        is BrowserCommand.Click, is BrowserCommand.LongPress ->
            dev.shantoislam.agenticwebview.api.ActionExecutionStrategy.DOM_POINTER
        is BrowserCommand.TypeText -> dev.shantoislam.agenticwebview.api.ActionExecutionStrategy.DOM_NATIVE_SETTER
        is BrowserCommand.SelectOption -> dev.shantoislam.agenticwebview.api.ActionExecutionStrategy.DOM_SELECT
        is BrowserCommand.PressKeys -> dev.shantoislam.agenticwebview.api.ActionExecutionStrategy.DOM_KEYBOARD
        is BrowserCommand.Scroll, is BrowserCommand.ScrollIntoView ->
            dev.shantoislam.agenticwebview.api.ActionExecutionStrategy.DOM_SCROLL
    }

    private fun BrowserCommand.stableTypeName() = when (this) {
        is BrowserCommand.Click -> "click"
        is BrowserCommand.LongPress -> "long_press"
        is BrowserCommand.TypeText -> "type_text"
        is BrowserCommand.SelectOption -> "select_option"
        is BrowserCommand.PressKeys -> "press_keys"
        is BrowserCommand.Scroll -> "scroll"
        is BrowserCommand.ScrollIntoView -> "scroll_into_view"
    }

    private fun <T> gatewayFailure(result: GatewayResult, operation: String): BrowserResult<T> = when (result) {
        is GatewayResult.RequestRejected -> failure(BrowserError.RuntimeFailure("REQUEST_REJECTED", result.reason))
        is GatewayResult.ResponseRejected -> failure(BrowserError.MalformedRuntimeResponse(result.reason))
        is GatewayResult.PendingLimitExceeded -> failure(BrowserError.ResourceLimitExceeded("pending runtime requests", result.limit.toLong()))
        is GatewayResult.TimedOut -> failure(BrowserError.Timeout(operation, result.timeoutMs))
        is GatewayResult.Cancelled -> failure(BrowserError.Cancelled(operation, result.reason))
        is GatewayResult.DispatchFailed -> failure(BrowserError.RuntimeUnavailable(result.reason))
        GatewayResult.Closed -> failure(BrowserError.SessionClosed())
        is GatewayResult.Response -> error("Response must be handled before gatewayFailure")
    }

    private fun GatewayResult.safeDiagnosticSummary(): String = when (this) {
        is GatewayResult.Response -> "response:${envelope.status}:${envelope.error?.code ?: "none"}"
        is GatewayResult.RequestRejected -> "request_rejected"
        is GatewayResult.ResponseRejected -> "response_rejected"
        is GatewayResult.PendingLimitExceeded -> "pending_limit"
        is GatewayResult.TimedOut -> "timeout"
        is GatewayResult.Cancelled -> "cancelled"
        is GatewayResult.DispatchFailed -> "dispatch_failed"
        GatewayResult.Closed -> "closed"
    }

    private fun failure(error: BrowserError): BrowserResult.Failure = BrowserResult.Failure(error)

    private companion object {
        const val PROTOCOL_BRIDGE_NAME = "AgenticProtocolBridge"
        const val RUNTIME_ASSET_NAME = "agentic_runtime.min.js"
        val OBSERVABLE_PHASES = setOf(
            BrowserSessionPhase.INTERACTIVE,
            BrowserSessionPhase.STABILIZING,
            BrowserSessionPhase.READY,
        )
        const val ELEMENT_POLL_INTERVAL_MS = 100L
        const val POPUP_RELAY_TIMEOUT_MS = 5_000L
        const val GEOLOCATION_RESOURCE = "android.webkit.resource.GEOLOCATION"
        val SENSITIVE_DIAGNOSTIC_KEYS = setOf("url", "message", "reason", "result", "payload", "text")

        fun decodeJavascriptString(raw: String?): String? {
            if (raw == null || raw == "null" || raw == "undefined") return null
            return try {
                Json.decodeFromString<String>(raw)
            } catch (_: Exception) {
                raw.trim('"')
            }
        }
    }
}

@Serializable
private data class NativeClickPreparation(
    val token: String,
    val xCssPx: Double,
    val yCssPx: Double,
    val viewportWidthCssPx: Double,
    val viewportHeightCssPx: Double,
    val revisionBefore: ObservationRevision,
)
