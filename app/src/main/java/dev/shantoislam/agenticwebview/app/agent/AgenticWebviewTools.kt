package dev.shantoislam.agenticwebview.app.agent

import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import dev.shantoislam.agenticwebview.AgenticWebController
import dev.shantoislam.agenticwebview.models.AgentAction
import dev.shantoislam.agenticwebview.models.AgentResult
import dev.shantoislam.agenticwebview.models.ScrollDirection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AgenticWebviewTools(private val controller: AgenticWebController) : ToolSet {

    @Tool
    @LLMDescription("Captures the current state of the webview including the accessibility tree, viewport info, and URL.")
    suspend fun webview_get_state(): String {
        return when (val result = controller.captureState()) {
            is AgentResult.Success -> Json.encodeToString(result.data)
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }

    @Tool
    @LLMDescription("Navigates the webview to the specified URL.")
    suspend fun webview_navigate(url: String): String {
        return when (val result = controller.executeAction(AgentAction.Navigate(url))) {
            is AgentResult.Success -> "Navigated to $url"
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }

    @Tool
    @LLMDescription("Clicks on an element with the given agent ID.")
    suspend fun webview_click(agentId: String): String {
        return when (val result = controller.executeAction(AgentAction.Click(agentId))) {
            is AgentResult.Success -> "Clicked element $agentId"
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }

    @Tool
    @LLMDescription("Inputs text into an element with the given agent ID.")
    suspend fun webview_input_text(agentId: String, text: String): String {
        return when (val result = controller.executeAction(AgentAction.InputText(agentId, text))) {
            is AgentResult.Success -> "Typed text into $agentId"
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }

    @Tool
    @LLMDescription("Scrolls the webview in a specified direction (UP, DOWN, LEFT, RIGHT) by a ratio (0.0 to 1.0).")
    suspend fun webview_scroll(direction: String, ratio: Float = 0.5f): String {
        val scrollDirection = try {
            ScrollDirection.valueOf(direction.uppercase())
        } catch (e: Exception) {
            return "Error: Invalid direction $direction"
        }
        return when (val result = controller.executeAction(AgentAction.Scroll(scrollDirection, ratio))) {
            is AgentResult.Success -> "Scrolled ${scrollDirection.name}"
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }

    @Tool
    @LLMDescription("Navigates back in the browser history.")
    suspend fun webview_go_back(): String {
        return when (val result = controller.executeAction(AgentAction.GoBack)) {
            is AgentResult.Success -> "Navigated back"
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }

    @Tool
    @LLMDescription("Navigates forward in the browser history.")
    suspend fun webview_go_forward(): String {
        return when (val result = controller.executeAction(AgentAction.GoForward)) {
            is AgentResult.Success -> "Navigated forward"
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }

    @Tool
    @LLMDescription("Refreshes the current page.")
    suspend fun webview_refresh(): String {
        return when (val result = controller.executeAction(AgentAction.Refresh)) {
            is AgentResult.Success -> "Page refreshed"
            is AgentResult.Error -> "Error: ${result.error}"
        }
    }
}
