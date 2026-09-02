package dev.shantoislam.agenticwebview.webview

import android.content.Context
import android.os.Looper
import android.webkit.WebView
import dev.shantoislam.agenticwebview.api.AgenticBrowserConfiguration
import dev.shantoislam.agenticwebview.api.AgenticBrowserSession
import dev.shantoislam.agenticwebview.api.BrowserDiagnosticsSink
import dev.shantoislam.agenticwebview.api.BrowserScreenshotProvider

class AgenticBrowserHost private constructor(
    val view: WebView,
    val session: AgenticBrowserSession,
) : AutoCloseable {

    override fun close() {
        session.close()
    }

    fun onResume() {
        view.onResume()
    }

    fun onPause() {
        view.onPause()
    }

    companion object {
        @JvmStatic
        fun create(
            context: Context,
            configuration: AgenticBrowserConfiguration = AgenticBrowserConfiguration(),
            delegate: BrowserHostDelegate = BrowserHostDelegate.DenyAll,
            diagnosticsSink: BrowserDiagnosticsSink? = null,
            screenshotProvider: BrowserScreenshotProvider? = null,
        ): AgenticBrowserHost {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "AgenticBrowserHost.create must be called on the Android main thread"
            }
            configuration.requireValid()
            val webView = WebView(context)
            return try {
                val session = AndroidAgenticBrowserSession(
                    webView,
                    configuration,
                    ownsWebView = true,
                    delegate = delegate,
                    diagnosticsSink = diagnosticsSink,
                    screenshotProvider = screenshotProvider,
                )
                AgenticBrowserHost(webView, session)
            } catch (error: Exception) {
                webView.destroy()
                throw error
            }
        }

        @JvmStatic
        fun attach(
            webView: WebView,
            configuration: AgenticBrowserConfiguration = AgenticBrowserConfiguration(),
            delegate: BrowserHostDelegate = BrowserHostDelegate.DenyAll,
            diagnosticsSink: BrowserDiagnosticsSink? = null,
            screenshotProvider: BrowserScreenshotProvider? = null,
        ): AgenticBrowserHost {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "AgenticBrowserHost.attach must be called on the Android main thread"
            }
            configuration.requireValid()
            val session = AndroidAgenticBrowserSession(
                webView,
                configuration,
                ownsWebView = false,
                delegate = delegate,
                diagnosticsSink = diagnosticsSink,
                screenshotProvider = screenshotProvider,
            )
            return AgenticBrowserHost(webView, session)
        }
    }
}
