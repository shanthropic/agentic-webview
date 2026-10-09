package dev.shantoislam.agenticwebview.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.shantoislam.agenticwebview.api.AgenticBrowserConfiguration
import dev.shantoislam.agenticwebview.api.BrowserSessionPhase
import dev.shantoislam.agenticwebview.api.HistoryNavigationRequest
import dev.shantoislam.agenticwebview.api.NavigationOperation
import dev.shantoislam.agenticwebview.api.NavigationRequest
import dev.shantoislam.agenticwebview.app.ui.components.AgentComposer
import dev.shantoislam.agenticwebview.app.ui.components.BrowserTopBar
import dev.shantoislam.agenticwebview.app.ui.components.ChatMessageBubble
import dev.shantoislam.agenticwebview.app.ui.components.ReasoningSection
import dev.shantoislam.agenticwebview.app.ui.components.SettingsSheet
import dev.shantoislam.agenticwebview.app.ui.components.ToolCallCard
import dev.shantoislam.agenticwebview.compose.AgenticBrowserView
import dev.shantoislam.agenticwebview.compose.rememberAgenticBrowserHost
import dev.shantoislam.agenticwebview.tools.StandardBrowserTools
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AgenticWebViewModel,
    modifier: Modifier = Modifier,
) {
    val host = rememberAgenticBrowserHost(AgenticBrowserConfiguration())
    val tools = remember(host.session) { StandardBrowserTools(host.session) }
    val sessionState by host.session.state.collectAsState()

    val messages by viewModel.messages.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val thought by viewModel.thought.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true,
        ),
    )

    val isSheetExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded ||
        scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded

    // Back handling: collapse sheet if expanded
    BackHandler(enabled = isSheetExpanded) {
        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
    }

    // Scroll chat to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Initial navigation
    LaunchedEffect(Unit) {
        if (sessionState.url.isNullOrBlank()) {
            host.session.navigate(NavigationRequest("https://en.wikipedia.org/wiki/Kotlin_(programming_language)"))
        }
    }

    val isLoading = sessionState.phase in listOf(
        BrowserSessionPhase.NAVIGATING,
        BrowserSessionPhase.DOCUMENT_CREATED,
        BrowserSessionPhase.RUNTIME_INITIALIZING,
        BrowserSessionPhase.STABILIZING,
    )
    val loadingProgress = when (sessionState.phase) {
        BrowserSessionPhase.NAVIGATING -> 0.25f
        BrowserSessionPhase.DOCUMENT_CREATED -> 0.5f
        BrowserSessionPhase.RUNTIME_INITIALIZING -> 0.75f
        BrowserSessionPhase.STABILIZING -> 0.9f
        BrowserSessionPhase.READY -> 1.0f
        else -> 0.0f
    }

    val suggestedPrompts = remember {
        listOf(
            "Search Wikipedia for Kotlin",
            "Inspect page DOM structure",
            "Navigate to DuckDuckGo",
            "Scroll down the page",
        )
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 135.dp,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetDragHandle = { BottomSheetDefaults.DragHandle() },
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            scope.launch {
                                if (isSheetExpanded) {
                                    scaffoldState.bottomSheetState.partialExpand()
                                } else {
                                    scaffoldState.bottomSheetState.expand()
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.useSimulation) "Autonomous Agent (Simulation)" else "Autonomous Agent (${settings.modelName})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (isWorking) "Active • Executing web tasks" else "${messages.size} messages in conversation",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                if (isSheetExpanded) {
                                    scaffoldState.bottomSheetState.partialExpand()
                                } else {
                                    scaffoldState.bottomSheetState.expand()
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (isSheetExpanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                            contentDescription = if (isSheetExpanded) "Collapse drawer" else "Expand drawer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isSheetExpanded) {
                    // Chat Messages List
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(messages, key = { it.id }) { item ->
                            when (item) {
                                is ChatItem.User -> {
                                    ChatMessageBubble(
                                        isUser = true,
                                        content = item.text,
                                        timestamp = item.timestamp,
                                    )
                                }
                                is ChatItem.Assistant -> {
                                    ChatMessageBubble(
                                        isUser = false,
                                        content = item.text,
                                        timestamp = item.timestamp,
                                    )
                                }
                                is ChatItem.ToolCall -> {
                                    ToolCallCard(
                                        toolName = item.toolName,
                                        argsJson = item.argsJson,
                                        status = item.status,
                                        resultJson = item.resultJson,
                                    )
                                }
                            }
                        }

                        if (isThinking || thought.isNotBlank()) {
                            item(key = "thinking_indicator") {
                                ReasoningSection(
                                    thought = thought,
                                    isThinking = isThinking,
                                )
                            }
                        }
                    }

                    // Composer at bottom of expanded sheet
                    AgentComposer(
                        draft = draft,
                        onDraftChange = { viewModel.onDraftChange(it) },
                        onSend = { promptText ->
                            viewModel.sendPrompt(
                                promptText = promptText,
                                session = host.session,
                                dispatcher = tools,
                            )
                        },
                        onStop = { viewModel.stopAgent() },
                        isWorking = isWorking,
                        suggestedPrompts = suggestedPrompts,
                    )
                } else {
                    // Peek mode: Composer is visible right in the peek area
                    AgentComposer(
                        draft = draft,
                        onDraftChange = { viewModel.onDraftChange(it) },
                        onSend = { promptText ->
                            scope.launch {
                                scaffoldState.bottomSheetState.expand()
                            }
                            viewModel.sendPrompt(
                                promptText = promptText,
                                session = host.session,
                                dispatcher = tools,
                            )
                        },
                        onStop = { viewModel.stopAgent() },
                        isWorking = isWorking,
                        suggestedPrompts = suggestedPrompts,
                        onTap = {
                            scope.launch {
                                scaffoldState.bottomSheetState.expand()
                            }
                        },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            BrowserTopBar(
                url = sessionState.url ?: "",
                canGoBack = true,
                canGoForward = true,
                isLoading = isLoading,
                loadingProgress = loadingProgress,
                onBackClick = {
                    scope.launch {
                        host.session.navigateHistory(HistoryNavigationRequest(NavigationOperation.BACK))
                    }
                },
                onForwardClick = {
                    scope.launch {
                        host.session.navigateHistory(HistoryNavigationRequest(NavigationOperation.FORWARD))
                    }
                },
                onRefreshClick = {
                    scope.launch {
                        host.session.navigateHistory(HistoryNavigationRequest(NavigationOperation.RELOAD))
                    }
                },
                onSettingsClick = { showSettings = true },
                onNavigate = { targetUrl ->
                    scope.launch {
                        host.session.navigate(NavigationRequest(targetUrl))
                    }
                },
            )

            AgenticBrowserView(
                host = host,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            onSaveSettings = { viewModel.saveSettings(it) },
            onDismiss = { showSettings = false },
        )
    }
}
