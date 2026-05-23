package com.shantoislamdev.agenticwebview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.webkit.*
import com.shantoislamdev.agenticwebview.config.AgenticWebViewConfig
import com.shantoislamdev.agenticwebview.internal.SdkLogger
import com.shantoislamdev.agenticwebview.models.PageLifecycleState

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

    var listener: AgenticWebViewListener? = null

    companion object {
        const val BRIDGE_VERSION = 1

        fun init() {
            // Placeholder for library initialization
        }

        fun getVersion(): String = "0.1.0"
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
        setDataDirectorySuffix("agentic_webview")
    }

    private fun setupClients() {
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageLifecycleState = PageLifecycleState.LOADING
                listener?.onStateChanged(pageLifecycleState)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                injectScript()
                // Initial settlement - will be refined in controller
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

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                pageLifecycleState = PageLifecycleState.CRASHED
                listener?.onStateChanged(pageLifecycleState)
                logger.e("WebView", "Renderer process gone. Did crash: ${detail?.didCrash()}")
                
                if (detail?.didCrash() == true) {
                    listener?.onCrash(didRecover = true)
                    // In a real implementation, the host app might need to recreate the view.
                    // For now, we signal the crash and rely on the controller/host to handle it.
                }
                return true // Prevent app crash
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                listener?.onProgressChanged(newProgress)
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

    private fun injectScript() {
        if (scriptCache == null) {
            try {
                scriptCache = context.assets.open("agentic_core.min.js").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                logger.e("WebView", "Failed to load script from assets", e)
                return
            }
        }
        evaluateJavascript(scriptCache!!, null)
    }

    fun updatePageState(state: PageLifecycleState) {
        pageLifecycleState = state
        listener?.onStateChanged(state)
    }

    inner class JsBridge {
        private val mainHandler = Handler(Looper.getMainLooper())

        @JavascriptInterface
        fun onDomUpdate(version: Int, json: String) {
            if (version != BRIDGE_VERSION) {
                logger.w("Bridge", "Version mismatch: Expected $BRIDGE_VERSION, got $version. Re-injecting.")
                mainHandler.post { injectScript() }
                return
            }
            mainHandler.post {
                listener?.onDomMutated(json)
            }
        }

        @JavascriptInterface
        fun onError(errorJson: String) {
            logger.e("Bridge", "JS Error: $errorJson")
        }

        @JavascriptInterface
        fun resolvePromise(promiseId: String, result: String) {
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
    }
}
