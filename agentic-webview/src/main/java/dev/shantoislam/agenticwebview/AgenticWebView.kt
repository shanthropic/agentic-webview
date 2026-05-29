package dev.shantoislam.agenticwebview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.webkit.*
import dev.shantoislam.agenticwebview.config.AgenticWebViewConfig
import dev.shantoislam.agenticwebview.internal.SdkLogger
import dev.shantoislam.agenticwebview.models.PageLifecycleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@SuppressLint("SetJavaScriptEnabled")
class AgenticWebView @JvmOverloads constructor(
    context: Context,
    private val config: AgenticWebViewConfig = AgenticWebViewConfig(),
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private val logger = SdkLogger(config.enableDebugLogging)
    private var scriptCache: String? = null
    var pageLifecycleState: PageLifecycleState = PageLifecycleState.IDLE
        private set

    @Volatile
    private var currentSessionToken: String = ""

    @Volatile
    var isScriptInjected: Boolean = false
        private set

    @Volatile
    var lastNavigationHttpError: Int? = null
        internal set

    var listener: AgenticWebViewListener? = null

    companion object {
        private var isDataDirSet = false

        fun init() {}

        fun getVersion(): String = "0.2.1"
    }

    init {
        setupSettings()
        setupClients()
        addJavascriptInterface(JsBridge(), "AgenticBridge")
    }

    private fun setupSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = config.userAgent ?: userAgentString
        }
        if (!isDataDirSet) {
            try {
                setDataDirectorySuffix("agentic_webview")
                isDataDirSet = true
            } catch (e: Exception) {
                // Ignore if already set
            }
        }
    }

    private fun setupClients() {
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                currentSessionToken = java.util.UUID.randomUUID().toString()
                isScriptInjected = false
                lastNavigationHttpError = null
                pageLifecycleState = PageLifecycleState.LOADING
                listener?.onStateChanged(pageLifecycleState)

                if (config.enableAntiDetection) {
                    view?.evaluateJavascript("""
                        (function() {
                            try { Object.defineProperty(navigator, 'webdriver', { get: function() { return undefined; } }); } catch(e) {}
                            try { window.chrome = { runtime: {} }; } catch(e) {}
                        })();
                    """.trimIndent(), null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                // Do NOT blindly inject script here. We use JIT injection in ensureEngineAvailable.
                pageLifecycleState = PageLifecycleState.INTERACTIVE
                listener?.onStateChanged(pageLifecycleState)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val scheme = url.scheme
                if (scheme != "http" && scheme != "https") {
                    logger.w("WebView", "Blocking non-http(s) URL: $url")
                    return true
                }
                config.deniedHosts?.let { denied ->
                    val host = url.host ?: return@let
                    if (denied.any { host.endsWith(it) }) {
                        logger.w("WebView", "Blocking denied host: $host")
                        config.homeUrl?.let { view?.loadUrl(it) }
                        return true
                    }
                }
                config.allowedHosts?.let { hosts ->
                    if (url.host !in hosts) {
                        logger.w("WebView", "Blocking unauthorized host: ${url.host}")
                        return true
                    }
                }
                return false
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    pageLifecycleState = PageLifecycleState.ERROR
                    listener?.onStateChanged(pageLifecycleState)
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true) {
                    lastNavigationHttpError = errorResponse?.statusCode
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                pageLifecycleState = PageLifecycleState.CRASHED
                isScriptInjected = false
                listener?.onStateChanged(pageLifecycleState)
                logger.e("WebView", "Renderer process gone. Did crash: ${detail?.didCrash()}")
                if (detail?.didCrash() == true) {
                    listener?.onCrash(didRecover = true)
                }
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                listener?.onProgressChanged(newProgress)
            }

            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val newWebView = WebView(context)
                newWebView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                        url?.let { listener?.onNewTabRequested(it) }
                        newWebView.destroy()
                    }
                }
                transport.webView = newWebView
                resultMsg.sendToTarget()
                return true
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                logger.i("WebView", "JS Alert: $message")
                result?.confirm()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                logger.i("WebView", "JS Confirm: $message")
                result?.confirm()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                logger.i("WebView", "JS Prompt: $message")
                result?.confirm()
                return true
            }
        }
    }

    /**
     * Just-In-Time (JIT) script injection that guarantees the engine is available.
     * Uses a robust try-catch wrapper to report exact execution errors.
     * Returns an empty string on success, or an error message on failure.
     */
    suspend fun ensureEngineAvailable(): String = withContext(Dispatchers.Main) {
        if (isScriptInjected) {
            // Verify the global is still accessible (page may have navigated)
            val verified = suspendCancellableCoroutine { cont ->
                evaluateJavascript("typeof window.__AgenticInternal !== 'undefined'") { result ->
                    cont.resume(result == "true")
                }
            }
            if (verified) return@withContext ""
            // Global gone — re-inject
            isScriptInjected = false
        }

        if (scriptCache == null) {
            try {
                scriptCache = context.assets.open("agentic_core.min.js").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                val errorMsg = "Failed to load script from assets: ${e.message}"
                logger.e("WebView", errorMsg, e)
                return@withContext errorMsg
            }
        }

        val wrappedScript = """
            (function() {
                try {
                    if (typeof window.__AgenticInternal !== 'undefined') {
                        return 'SUCCESS';
                    }
                    ${scriptCache}
                    
                    if (typeof window.__AgenticInternal !== 'undefined') {
                        window.__AgenticInternal.setSessionToken('$currentSessionToken');
                        return 'SUCCESS';
                    } else {
                        return 'ERROR: Script executed but window.__AgenticInternal is still undefined.';
                    }
                } catch (e) {
                    return 'ERROR: ' + e.message + '\n' + e.stack;
                }
            })();
        """.trimIndent()

        suspendCancellableCoroutine { cont ->
            evaluateJavascript(wrappedScript) { result ->
                // The result is a JSON string, so "SUCCESS" becomes "\"SUCCESS\""
                val unquotedResult = if (result != null && result.startsWith("\"") && result.endsWith("\"")) {
                    result.substring(1, result.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                } else {
                    result ?: "ERROR: evaluateJavascript returned null"
                }

                if (unquotedResult == "SUCCESS") {
                    isScriptInjected = true
                    forwardConfigToEngine()
                    cont.resume("")
                } else {
                    val errorMsg = "Injection failed: $unquotedResult"
                    logger.e("WebView", errorMsg)
                    cont.resume(errorMsg)
                }
            }
        }
    }

    fun updatePageState(state: PageLifecycleState) {
        pageLifecycleState = state
        listener?.onStateChanged(state)
    }

    private fun forwardConfigToEngine() {
        val configJson = buildString {
            append("{")
            append("\"viewportExpansion\":${config.viewportExpansion},")
            append("\"domMutationThrottleMs\":${config.domMutationThrottleMs},")
            append("\"enableAntiDetection\":${config.enableAntiDetection}")
            config.includeAttributes?.let { attrs ->
                append(",\"includeAttributes\":[")
                append(attrs.joinToString(",") { "\"$it\"" })
                append("]")
            }
            append("}")
        }
        evaluateJavascript("window.__AgenticInternal && window.__AgenticInternal.configure('$configJson')", null)
    }

    inner class JsBridge {
        private val mainHandler = Handler(Looper.getMainLooper())

        @JavascriptInterface
        fun onDomUpdate(token: String, json: String) {
            if (token != currentSessionToken) {
                logger.w("Bridge", "Security alert: Unauthorized token in onDomUpdate")
                return
            }
            mainHandler.post {
                listener?.onDomMutated(json)
            }
        }

        @JavascriptInterface
        fun onError(token: String, errorJson: String) {
            if (token != currentSessionToken) {
                logger.w("Bridge", "Security alert: Unauthorized token in onError")
                return
            }
            logger.e("Bridge", "JS Error: $errorJson")
        }

        @JavascriptInterface
        fun resolvePromise(token: String, promiseId: String, result: String) {
            if (token != currentSessionToken) {
                logger.w("Bridge", "Security alert: Unauthorized token in resolvePromise")
                return
            }
            mainHandler.post {
                listener?.onPromiseResolved(promiseId, result)
            }
        }
    }

    interface AgenticWebViewListener {
        fun onStateChanged(state: PageLifecycleState)
        fun onProgressChanged(progress: Int)
        fun onDomMutated(json: String)
        fun onPromiseResolved(promiseId: String, result: String)
        fun onCrash(didRecover: Boolean)
        fun onNewTabRequested(url: String) {}
    }
}
