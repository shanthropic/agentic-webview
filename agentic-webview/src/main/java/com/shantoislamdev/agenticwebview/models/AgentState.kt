package com.shantoislamdev.agenticwebview.models

import kotlinx.serialization.Serializable

@Serializable
data class AgentState(
    val accessibilityTree: String,
    val screenshotBase64: String?,
    val viewportInfo: ViewportInfo,
    val url: String,
    val title: String,
    val pageState: PageLifecycleState,
    val elementCount: Int,
    val truncated: Boolean
)

@Serializable
data class ViewportInfo(
    val devicePixelRatio: Double,
    val visualViewportScale: Double,
    val scrollX: Int,
    val scrollY: Int,
    val viewportWidth: Int,
    val viewportHeight: Int
)
