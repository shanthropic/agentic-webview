package com.shantoislamdev.agenticwebview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shantoislamdev.agenticwebview.config.AgenticWebViewConfig

@Composable
fun AgenticWebViewComposable(
    controller: AgenticWebController,
    modifier: Modifier = Modifier,
    config: AgenticWebViewConfig = AgenticWebViewConfig()
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { context ->
            AgenticWebView(context, config).also { webView ->
                controller.attach(webView)
            }
        },
        modifier = modifier,
        onRelease = {
            controller.detach()
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> controller.pauseTimers()
                Lifecycle.Event.ON_RESUME -> controller.resumeTimers()
                Lifecycle.Event.ON_DESTROY -> controller.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
