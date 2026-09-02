package dev.shantoislam.agenticwebview.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import dev.shantoislam.agenticwebview.api.AgenticBrowserConfiguration
import dev.shantoislam.agenticwebview.api.BrowserDiagnosticsSink
import dev.shantoislam.agenticwebview.webview.AgenticBrowserHost
import dev.shantoislam.agenticwebview.webview.BrowserHostDelegate

/**
 * Creates and owns an [AgenticBrowserHost] for the lifetime of this composition.
 *
 * A configuration change intentionally creates a fresh browser session. This avoids carrying
 * runtime policy and protocol state across incompatible configurations.
 */
@Composable
fun rememberAgenticBrowserHost(
    configuration: AgenticBrowserConfiguration = AgenticBrowserConfiguration(),
    delegate: BrowserHostDelegate = BrowserHostDelegate.DenyAll,
    diagnosticsSink: BrowserDiagnosticsSink? = null,
): AgenticBrowserHost {
    val context = LocalContext.current
    val host = remember(context, configuration, delegate, diagnosticsSink) {
        AgenticBrowserHost.create(context, configuration, delegate, diagnosticsSink)
    }
    DisposableEffect(host) {
        onDispose(host::close)
    }
    return host
}

/** Places an existing [AgenticBrowserHost] in the Compose UI hierarchy. */
@Composable
fun AgenticBrowserView(
    host: AgenticBrowserHost,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    DisposableEffect(host, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> host.onResume()
                Lifecycle.Event.ON_PAUSE -> host.onPause()
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
            host.onResume()
        }
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            host.onPause()
        }
    }
    AndroidView(
        factory = { host.view },
        modifier = modifier,
    )
}
