package dev.shantoislam.agenticwebview.internal

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.view.PixelCopy
import androidx.core.graphics.createBitmap
import dev.shantoislam.agenticwebview.AgenticWebView
import dev.shantoislam.agenticwebview.models.AgentError
import dev.shantoislam.agenticwebview.models.AgentResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

internal class ScreenshotCapture(
    private val webViewProvider: () -> AgenticWebView?,
    private val quality: Int,
    private val maxDimension: Int,
    private val logger: SdkLogger
) {
    private val pixelCopyThread = HandlerThread("PixelCopyThread").apply { start() }
    private val pixelCopyHandler = Handler(pixelCopyThread.looper)
    private var cachedBitmap: Bitmap? = null

    suspend fun capture(): AgentResult<String> {
        val wv = webViewProvider()
            ?: return AgentResult.Error(AgentError.ScreenshotFailed("WebView is null"))
        if (wv.width <= 0 || wv.height <= 0)
            return AgentResult.Error(AgentError.ScreenshotFailed("WebView has zero dimensions"))

        val window = (wv.context as? Activity)?.window
            ?: return AgentResult.Error(AgentError.ScreenshotFailed("No Activity window"))
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
                val scale = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height, 1f)
                val finalBitmap = if (scale < 1f) {
                    val scaledW = (bitmap.width * scale).toInt()
                    val scaledH = (bitmap.height * scale).toInt()
                    Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
                } else bitmap

                val outputStream = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                if (finalBitmap !== bitmap) finalBitmap.recycle()
                AgentResult.Success(Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP))
            } else {
                logger.e("Screenshot", "PixelCopy failed with code: $result")
                AgentResult.Error(AgentError.ScreenshotFailed("PixelCopy failed with code $result"))
            }
        } catch (e: Exception) {
            logger.e("Screenshot", "Failed to capture screenshot", e)
            AgentResult.Error(AgentError.ScreenshotFailed(e.message ?: "Unknown error"))
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

    fun destroy() {
        pixelCopyThread.quitSafely()
        cachedBitmap?.recycle()
        cachedBitmap = null
    }
}
