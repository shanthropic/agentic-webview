# Jetpack Compose integration

The `browser-compose` artifact provides declarative Jetpack Compose bindings for Agentic WebView:
- `rememberAgenticBrowserHost`: creates and manages the lifecycle of an `AgenticBrowserHost`.
- `AgenticBrowserView`: renders the underlying `WebView` inside the Compose hierarchy and synchronizes Android lifecycle state.

## Dependency setup

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.shantoislam.agenticwebview:browser-webview:0.3.0")
    implementation("dev.shantoislam.agenticwebview:browser-compose:0.3.0")
}
```

---

## Reactive Compose recipe

Here is a complete, compile-ready Composable screen showcasing:
1. `rememberAgenticBrowserHost` with an explicit `BrowserHostDelegate`.
2. Reactive state observation via `host.session.state.collectAsState()`.
3. Event stream monitoring via `LaunchedEffect(host)`.
4. Interactive navigation and command dispatch via coroutines.

```kotlin
package com.example.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.shantoislam.agenticwebview.api.AgenticBrowserConfiguration
import dev.shantoislam.agenticwebview.api.BrowserEvent
import dev.shantoislam.agenticwebview.api.BrowserSessionPhase
import dev.shantoislam.agenticwebview.api.HostRule
import dev.shantoislam.agenticwebview.api.NavigationPolicy
import dev.shantoislam.agenticwebview.api.NavigationRequest
import dev.shantoislam.agenticwebview.api.WaitCondition
import dev.shantoislam.agenticwebview.compose.AgenticBrowserView
import dev.shantoislam.agenticwebview.compose.rememberAgenticBrowserHost
import dev.shantoislam.agenticwebview.webview.BrowserDialogDecision
import dev.shantoislam.agenticwebview.webview.BrowserDialogRequest
import dev.shantoislam.agenticwebview.webview.BrowserHostDelegate
import dev.shantoislam.agenticwebview.webview.BrowserPopupDecision
import dev.shantoislam.agenticwebview.webview.BrowserPopupRequest
import kotlinx.coroutines.launch

@Composable
fun AgenticBrowserScreen() {
    val coroutineScope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("https://example.com") }

    // 1. Define host configuration
    val configuration = remember {
        AgenticBrowserConfiguration(
            navigation = NavigationPolicy(
                allowedSchemes = setOf("https"),
                allowedHosts = setOf(HostRule.DomainAndSubdomains("example.com")),
            ),
        )
    }

    // 2. Define host delegate (or use BrowserHostDelegate.DenyAll)
    val delegate = remember {
        object : BrowserHostDelegate {
            override fun onDialog(request: BrowserDialogRequest): BrowserDialogDecision {
                return BrowserDialogDecision.Confirm(promptValue = request.defaultValue)
            }
            override fun onPopup(request: BrowserPopupRequest): BrowserPopupDecision {
                return BrowserPopupDecision.OPEN_IN_CURRENT_SESSION
            }
        }
    }

    // 3. Remember the browser host
    val host = rememberAgenticBrowserHost(
        configuration = configuration,
        delegate = delegate,
    )

    // 4. Collect reactive session state
    val sessionState by host.session.state.collectAsState()

    // 5. React to session events
    LaunchedEffect(host) {
        host.session.events.collect { event ->
            when (event) {
                is BrowserEvent.StateChanged -> {
                    println("State phase changed: ${event.current.phase}")
                }
                is BrowserEvent.NavigationBlocked -> {
                    println("Navigation was blocked: ${event.url} - ${event.reason}")
                }
                is BrowserEvent.RendererTerminated -> {
                    println("Renderer terminated! Crash status: ${event.didCrash}")
                }
                else -> Unit
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Address Bar and Action Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("URL") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        host.session.navigate(
                            NavigationRequest(
                                url = urlInput,
                                readiness = WaitCondition.PageReady,
                            )
                        )
                    }
                },
                enabled = sessionState.phase != BrowserSessionPhase.NAVIGATING,
            ) {
                Text("Go")
            }
        }

        // Status bar showing phase, URL, and revision
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF0F0F0))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sessionState.phase == BrowserSessionPhase.NAVIGATING) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "Phase: ${sessionState.phase} | Rev: ${sessionState.revision.value} | URL: ${sessionState.url ?: "None"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // 6. Embed the browser view
        AgenticBrowserView(
            host = host,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}
```

---

## Lifecycle & Configuration Identity

- `rememberAgenticBrowserHost` owns creation and disposal (`host.close()`). When the Composable leaves composition, the underlying WebView and session are cleanly destroyed.
- **Identity semantics**: If `configuration` or `delegate` changes identity, a fresh `AgenticBrowserHost` is created. This ensures runtime policies and JavaScript bridge states cannot drift silently under an active document.
- `AgenticBrowserView` listens to the Compose window's `LifecycleOwner` and automatically dispatches `host.onResume()` and `host.onPause()` without requiring manual Activity overrides.

---

## Related documentation

- [Android Views integration](views.md)
- [Configuration cookbook](configuration.md)
- [Command reference](commands.md)
- [Lifecycle & event stream](lifecycle.md)
- [Framework-neutral agent tools](agent-tools.md)
