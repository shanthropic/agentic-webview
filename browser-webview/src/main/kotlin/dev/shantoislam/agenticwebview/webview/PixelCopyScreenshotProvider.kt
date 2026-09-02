package dev.shantoislam.agenticwebview.webview

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.webkit.WebView
import dev.shantoislam.agenticwebview.api.BrowserScreenshot
import dev.shantoislam.agenticwebview.api.BrowserError
import dev.shantoislam.agenticwebview.api.BrowserResult
import dev.shantoislam.agenticwebview.api.BrowserScreenshotProvider
import dev.shantoislam.agenticwebview.api.ScreenshotConfiguration
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal class PixelCopyScreenshotProvider(
    private val webViewProvider: () -> WebView?,
    private val configuration: ScreenshotConfiguration,
) : BrowserScreenshotProvider {
    private val closed = AtomicBoolean(false)
    private val workerThread = HandlerThread("AgenticWebViewScreenshot").apply { start() }
    private val workerHandler = Handler(workerThread.looper)

    override suspend fun capture(): BrowserResult<BrowserScreenshot> {
        if (closed.get()) return failure("Screenshot provider is closed")

        val source = withContext(Dispatchers.Main.immediate) {
            val webView = webViewProvider()
                ?: return@withContext null
            if (!webView.isAttachedToWindow || webView.width <= 0 || webView.height <= 0) {
                return@withContext null
            }
            val activity = webView.context.findActivity()
                ?: return@withContext null
            val location = IntArray(2)
            webView.getLocationInWindow(location)
            val rect = Rect(
                location[0],
                location[1],
                location[0] + webView.width,
                location[1] + webView.height,
            )
            val windowBounds = Rect(0, 0, activity.window.decorView.width, activity.window.decorView.height)
            if (!rect.intersect(windowBounds) || rect.width() <= 0 || rect.height() <= 0) {
                return@withContext null
            }
            ScreenshotSource(
                activity = activity,
                rect = rect,
                cropOffsetX = rect.left - location[0],
                cropOffsetY = rect.top - location[1],
                viewWidth = webView.width,
                viewHeight = webView.height,
                masks = captureMasks(webView),
            )
        } ?: return failure("WebView is not attached to an Activity window with positive dimensions")

        val bitmap = try {
            Bitmap.createBitmap(source.rect.width(), source.rect.height(), Bitmap.Config.ARGB_8888)
        } catch (error: Exception) {
            return failure("Unable to allocate screenshot bitmap: ${error.message}")
        }

        val copyResult = suspendCancellableCoroutine<Int> { continuation ->
            try {
                PixelCopy.request(source.activity.window, source.rect, bitmap, { result ->
                    if (continuation.isActive) continuation.resume(result)
                    else bitmap.recycle()
                }, workerHandler)
            } catch (error: Exception) {
                bitmap.recycle()
                if (continuation.isActive) continuation.resume(PIXEL_COPY_DISPATCH_FAILED)
            }
        }
        if (copyResult != PixelCopy.SUCCESS) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return failure("PixelCopy failed with code $copyResult")
        }

        applyMasks(bitmap, source)

        return withContext(Dispatchers.Default) {
            encode(bitmap)
        }
    }

    private fun encode(source: Bitmap): BrowserResult<BrowserScreenshot> {
        var outputBitmap = source
        return try {
            val scale = minOf(
                1f,
                configuration.maximumDimensionPx.toFloat() / source.width,
                configuration.maximumDimensionPx.toFloat() / source.height,
            )
            if (scale < 1f) {
                outputBitmap = Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            }

            val output = ByteArrayOutputStream()
            val encoded = outputBitmap.compress(Bitmap.CompressFormat.JPEG, configuration.jpegQuality, output)
            if (!encoded) return failure("Bitmap JPEG encoding failed")
            val bytes = output.toByteArray()
            if (bytes.size > configuration.maximumEncodedBytes) {
                failure(
                    "Encoded screenshot is ${bytes.size} bytes; limit is ${configuration.maximumEncodedBytes}",
                )
            } else {
                BrowserResult.Success(
                    BrowserScreenshot(
                        bytes = bytes,
                        mimeType = "image/jpeg",
                        widthPx = outputBitmap.width,
                        heightPx = outputBitmap.height,
                    ),
                )
            }
        } catch (error: Exception) {
            failure("Screenshot encoding failed: ${error.message}")
        } finally {
            if (outputBitmap !== source && !outputBitmap.isRecycled) outputBitmap.recycle()
            if (!source.isRecycled) source.recycle()
        }
    }

    private suspend fun captureMasks(webView: WebView): ScreenshotMaskData? {
        if (configuration.maskCssSelectors.isEmpty()) return null
        val selectors = JSONArray(configuration.maskCssSelectors).toString()
        val script = """
            (function() {
                const selectors = $selectors;
                const rects = [];
                const visited = new WeakSet();
                function collect(root, offsetX, offsetY, depth) {
                    if (!root || visited.has(root) || depth > 32) return;
                    visited.add(root);
                    for (const selector of selectors) {
                        try {
                            for (const element of root.querySelectorAll(selector)) {
                                const r = element.getBoundingClientRect();
                                if (r.width > 0 && r.height > 0) {
                                    rects.push([offsetX + r.left, offsetY + r.top, r.width, r.height]);
                                }
                            }
                        } catch (_) {}
                    }
                    for (const element of root.querySelectorAll('*')) {
                        if (element.shadowRoot) collect(element.shadowRoot, offsetX, offsetY, depth + 1);
                        if (element.tagName === 'IFRAME') {
                            try {
                                const r = element.getBoundingClientRect();
                                collect(element.contentDocument, offsetX + r.left, offsetY + r.top, depth + 1);
                            } catch (_) {}
                        }
                    }
                }
                collect(document, 0, 0, 0);
                return JSON.stringify({ width: window.innerWidth, height: window.innerHeight, rects });
            })();
        """.trimIndent()
        val raw = suspendCancellableCoroutine<String?> { continuation ->
            webView.evaluateJavascript(script) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        } ?: return null
        return try {
            val encoded = JSONTokener(raw).nextValue() as? String ?: return null
            val root = JSONObject(encoded)
            val viewportWidth = root.optDouble("width", 0.0)
            val viewportHeight = root.optDouble("height", 0.0)
            if (viewportWidth <= 0.0 || viewportHeight <= 0.0) return null
            val entries = root.optJSONArray("rects") ?: JSONArray()
            val rects = buildList {
                for (index in 0 until entries.length()) {
                    val value = entries.optJSONArray(index) ?: continue
                    if (value.length() != 4) continue
                    add(
                        CssMaskRect(
                            value.optDouble(0),
                            value.optDouble(1),
                            value.optDouble(2),
                            value.optDouble(3),
                        ),
                    )
                }
            }
            ScreenshotMaskData(viewportWidth, viewportHeight, rects)
        } catch (_: Exception) {
            null
        }
    }

    private fun applyMasks(bitmap: Bitmap, source: ScreenshotSource) {
        val masks = source.masks ?: return
        val scaleX = source.viewWidth / masks.viewportWidth
        val scaleY = source.viewHeight / masks.viewportHeight
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.BLACK }
        for (mask in masks.rects) {
            val left = (mask.left * scaleX - source.cropOffsetX).toFloat()
            val top = (mask.top * scaleY - source.cropOffsetY).toFloat()
            val right = (left + mask.width * scaleX).toFloat()
            val bottom = (top + mask.height * scaleY).toFloat()
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        workerThread.quitSafely()
    }

    private data class ScreenshotSource(
        val activity: Activity,
        val rect: Rect,
        val cropOffsetX: Int,
        val cropOffsetY: Int,
        val viewWidth: Int,
        val viewHeight: Int,
        val masks: ScreenshotMaskData?,
    )

    private data class ScreenshotMaskData(
        val viewportWidth: Double,
        val viewportHeight: Double,
        val rects: List<CssMaskRect>,
    )

    private data class CssMaskRect(
        val left: Double,
        val top: Double,
        val width: Double,
        val height: Double,
    )

    private companion object {
        const val PIXEL_COPY_DISPATCH_FAILED = -1

        fun failure(message: String): BrowserResult.Failure =
            BrowserResult.Failure(BrowserError.ScreenshotFailure(message))
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> if (baseContext === this) null else baseContext.findActivity()
        else -> null
    }
}
