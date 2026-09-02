package dev.shantoislam.agenticwebview.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shantoislam.agenticwebview.api.AgenticBrowserConfiguration
import dev.shantoislam.agenticwebview.api.BrowserResult
import dev.shantoislam.agenticwebview.api.NavigationRequest
import dev.shantoislam.agenticwebview.app.ui.theme.AppTheme
import dev.shantoislam.agenticwebview.compose.AgenticBrowserView
import dev.shantoislam.agenticwebview.compose.rememberAgenticBrowserHost
import dev.shantoislam.agenticwebview.tools.StandardBrowserTools
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                SampleBrowser()
            }
        }
    }
}

@Composable
private fun SampleBrowser() {
    val host = rememberAgenticBrowserHost(AgenticBrowserConfiguration())
    val tools = remember(host.session) { StandardBrowserTools(host.session) }
    val state by host.session.state.collectAsState()
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("https://example.com") }
    var output by remember { mutableStateOf("Use Navigate, then inspect the typed observation or generic tool output.") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Agentic WebView", style = MaterialTheme.typography.titleLarge)
            Text("Session: ${state.phase} • revision ${state.revision.value}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    scope.launch {
                        output = when (val result = host.session.navigate(NavigationRequest(url))) {
                            is BrowserResult.Success -> "Ready: ${result.value.finalUrl}"
                            is BrowserResult.Failure -> "Navigation failed: ${result.error.message}"
                        }
                    }
                }) {
                    Text("Navigate")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        output = when (val result = host.session.observe()) {
                            is BrowserResult.Success ->
                                "Typed observation: ${result.value.nodes.size} nodes, revision ${result.value.revision.value}"
                            is BrowserResult.Failure -> "Observation failed: ${result.error.message}"
                        }
                    }
                }) {
                    Text("Typed observe")
                }
                Button(onClick = {
                    scope.launch {
                        output = tools.invoke("browser_observe", buildJsonObject {}).output.toString()
                    }
                }) {
                    Text("Agent tool observe")
                }
            }
            AgenticBrowserView(
                host = host,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Text(output, style = MaterialTheme.typography.bodySmall)
        }
    }
}
