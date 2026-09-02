package dev.shantoislam.agenticwebview.webview

import android.net.Uri

/** Synchronous decisions for requests that require host application authority. */
interface BrowserHostDelegate {
    fun onDialog(request: BrowserDialogRequest): BrowserDialogDecision = BrowserDialogDecision.Cancel
    fun onPopup(request: BrowserPopupRequest): BrowserPopupDecision = BrowserPopupDecision.DENY
    fun onPermission(request: BrowserPermissionRequest): Set<String> = emptySet()
    fun onDownload(request: BrowserDownloadRequest) = Unit
    fun onFileChooser(request: BrowserFileChooserRequest, respond: (Array<Uri>?) -> Unit): Boolean = false
    fun onNavigationBlocked(url: String, reason: String) = Unit
    fun onSslError(url: String, primaryError: Int) = Unit
    fun onSafeBrowsingHit(url: String, threatType: Int) = Unit

    companion object {
        val DenyAll: BrowserHostDelegate = object : BrowserHostDelegate {}
    }
}

data class BrowserDialogRequest(
    val type: dev.shantoislam.agenticwebview.api.DialogType,
    val message: String,
    val defaultValue: String?,
)

sealed interface BrowserDialogDecision {
    data class Confirm(val promptValue: String? = null) : BrowserDialogDecision
    data object Cancel : BrowserDialogDecision
}

data class BrowserPopupRequest(val urlHint: String?, val isUserGesture: Boolean)

enum class BrowserPopupDecision { OPEN_IN_CURRENT_SESSION, DENY }

data class BrowserPermissionRequest(val origin: String, val resources: Set<String>)

data class BrowserDownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val contentLength: Long,
)

data class BrowserFileChooserRequest(
    val acceptTypes: List<String>,
    val captureEnabled: Boolean,
    val allowsMultiple: Boolean,
)
