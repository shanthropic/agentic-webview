package com.shantoislamdev.agenticwebview.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shantoislamdev.agenticwebview.AgenticWebView
import com.shantoislamdev.agenticwebview.AgenticWebViewComposable
import com.shantoislamdev.agenticwebview.app.ui.AgenticWebViewModel
import com.shantoislamdev.agenticwebview.app.ui.ChatMessage
import com.shantoislamdev.agenticwebview.app.ui.AgentSettings
import com.shantoislamdev.agenticwebview.app.ui.theme.WebviewAgentTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AgenticWebViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgenticWebView.init()
        enableEdgeToEdge()
        setContent {
            WebviewAgentTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AgenticWebViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    val messages = viewModel.messages
    val isThinking by viewModel.isThinking.collectAsState()
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BrowserTopBar(onNavigate = { url -> 
                // Navigation logic
            })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // WebView Area (55%)
            Box(modifier = Modifier.weight(0.55f)) {
                AgenticWebViewComposable(
                    controller = viewModel.controller,
                    modifier = Modifier.fillMaxSize()
                )
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Agent Area (45%)
            Column(modifier = Modifier.weight(0.45f)) {
                AgentChatHeader(
                    isThinking = isThinking,
                    onSettingsClick = { showSettings = true }
                )
                
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    reverseLayout = false
                ) {
                    items(messages) { message ->
                        ChatBubble(message)
                    }
                }

                ChatInput(onSend = { viewModel.sendMessage(it) })
            }
        }

        if (showSettings) {
            SettingsSheet(
                settings = settings,
                onDismiss = { showSettings = false },
                onSave = { viewModel.updateSettings(it) }
            )
        }
    }
}

@Composable
fun BrowserTopBar(onNavigate: (String) -> Unit) {
    var urlText by remember { mutableStateOf("https://www.google.com") }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Back */ }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = { /* Forward */ }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = { /* Refresh */ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }

            TextField(
                value = urlText,
                onValueChange = { urlText = it },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp)),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
fun AgentChatHeader(isThinking: Boolean, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isThinking) Color.Yellow else Color.Green)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isThinking) "Thinking..." else "Idle",
                style = MaterialTheme.typography.labelMedium
            )
        }

        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.message,
                modifier = Modifier.padding(12.dp, 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ChatInput(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp)),
            placeholder = { Text("Type a message...") },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = {
                onSend(text)
                text = ""
            },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.Send, contentDescription = "Send")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AgentSettings,
    onDismiss: () -> Unit,
    onSave: (AgentSettings) -> Unit
) {
    var localSettings by remember { mutableStateOf(settings) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text("Agent Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Simulation Mode", modifier = Modifier.weight(1f))
                Switch(
                    checked = localSettings.useSimulation,
                    onCheckedChange = { localSettings = localSettings.copy(useSimulation = it) }
                )
            }

            if (!localSettings.useSimulation) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = localSettings.baseUrl,
                    onValueChange = { localSettings = localSettings.copy(baseUrl = it) },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = localSettings.apiKey,
                    onValueChange = { localSettings = localSettings.copy(apiKey = it) },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = localSettings.modelName,
                    onValueChange = { localSettings = localSettings.copy(modelName = it) },
                    label = { Text("Model Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    onSave(localSettings)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
