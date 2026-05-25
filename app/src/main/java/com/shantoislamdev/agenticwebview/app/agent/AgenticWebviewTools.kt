package com.shantoislamdev.agenticwebview.app.agent

import com.shantoislamdev.agenticwebview.AgenticWebController
import com.shantoislamdev.agenticwebview.models.AgentAction
import com.shantoislamdev.agenticwebview.models.AgentResult
import com.shantoislamdev.agenticwebview.models.ScrollDirection
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Koog-compatible Tool interface (Stub fallback)
interface Tool {
    val name: String
    val description: String
    suspend fun call(args: String): String
}

@Serializable
data class NavigateArgs(val url: String)

@Serializable
data class ClickArgs(val id: String)

@Serializable
data class InputTextArgs(val id: String, val text: String)

@Serializable
data class ScrollArgs(val direction: String, val ratio: Float = 0.5f)

class WebviewGetStateTool(private val controller: AgenticWebController) : Tool {
    override val name: String = "webview_get_state"
    override val description: String = "Captures the current state of the webview including the accessibility tree and screenshot."

    override suspend fun call(args: String): String {
        return when (val result = controller.captureState()) {
            is AgentResult.Success -> {
                val state = result.data
                "State captured: Title='${state.title}', URL='${state.url}', Nodes=${state.elementCount}"
            }
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }
}

class WebviewNavigateTool(private val controller: AgenticWebController) : Tool {
    override val name: String = "webview_navigate"
    override val description: String = "Navigates the webview to the specified URL."

    override suspend fun call(args: String): String {
        val parsedArgs = Json.decodeFromString<NavigateArgs>(args)
        return when (val result = controller.executeAction(AgentAction.Navigate(parsedArgs.url))) {
            is AgentResult.Success -> "Navigated to ${parsedArgs.url}"
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }
}

class WebviewClickTool(private val controller: AgenticWebController) : Tool {
    override val name: String = "webview_click"
    override val description: String = "Clicks on an element with the given agent ID."

    override suspend fun call(args: String): String {
        val parsedArgs = Json.decodeFromString<ClickArgs>(args)
        return when (val result = controller.executeAction(AgentAction.Click(parsedArgs.id))) {
            is AgentResult.Success -> "Clicked element ${parsedArgs.id}"
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }
}

class WebviewInputTextTool(private val controller: AgenticWebController) : Tool {
    override val name: String = "webview_input_text"
    override val description: String = "Inputs text into an element with the given agent ID."

    override suspend fun call(args: String): String {
        val parsedArgs = Json.decodeFromString<InputTextArgs>(args)
        return when (val result = controller.executeAction(AgentAction.InputText(parsedArgs.id, parsedArgs.text))) {
            is AgentResult.Success -> "Typed text into ${parsedArgs.id}"
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }
}

class WebviewScrollTool(private val controller: AgenticWebController) : Tool {
    override val name: String = "webview_scroll"
    override val description: String = "Scrolls the webview in a specified direction (UP, DOWN, LEFT, RIGHT) by a ratio (0.0 to 1.0)."

    override suspend fun call(args: String): String {
        val parsedArgs = Json.decodeFromString<ScrollArgs>(args)
        val direction = try {
            ScrollDirection.valueOf(parsedArgs.direction.uppercase())
        } catch (e: Exception) {
            return "Error: Invalid direction ${parsedArgs.direction}"
        }
        return when (val result = controller.executeAction(AgentAction.Scroll(direction, parsedArgs.ratio))) {
            is AgentResult.Success -> "Scrolled ${direction.name}"
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }
}
