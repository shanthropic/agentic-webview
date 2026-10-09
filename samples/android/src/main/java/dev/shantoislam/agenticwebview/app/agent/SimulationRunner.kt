package dev.shantoislam.agenticwebview.app.agent

import dev.shantoislam.agenticwebview.api.AgenticBrowserSession
import dev.shantoislam.agenticwebview.tools.AgentToolDispatcher
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SimulationRunner(
    private val session: AgenticBrowserSession,
    private val dispatcher: AgentToolDispatcher,
) {
    suspend fun runSimulation(
        userPrompt: String,
        onToolStarted: (id: String, name: String, args: String) -> Unit,
        onToolCompleted: (id: String, name: String, result: String) -> Unit,
        onToolFailed: (id: String, name: String, error: String) -> Unit,
        onThinkingChanged: (Boolean) -> Unit,
        onThoughtUpdate: (String) -> Unit,
    ): String {
        val promptLower = userPrompt.lowercase()

        // Branch 1: Scroll request
        if (promptLower.contains("scroll")) {
            onThinkingChanged(true)
            onThoughtUpdate("Detected scroll instruction. Preparing to scroll page viewport down by 400 CSS pixels.")
            delay(500)
            onThinkingChanged(false)

            val scrollId = UUID.randomUUID().toString()
            val scrollArgs = buildJsonObject {
                put("delta_x_css_px", 0.0)
                put("delta_y_css_px", 400.0)
            }
            onToolStarted(scrollId, "browser_scroll", scrollArgs.toString())
            delay(400)

            val result = dispatcher.invoke("browser_scroll", scrollArgs)
            if (result.success) {
                onToolCompleted(scrollId, "browser_scroll", result.output.toString())
            } else {
                onToolFailed(scrollId, "browser_scroll", result.output.toString())
            }

            onThinkingChanged(true)
            onThoughtUpdate("Page scroll completed. Ready for subsequent commands.")
            delay(400)
            onThinkingChanged(false)

            return "Scrolled the webpage down by 400 CSS pixels."
        }

        // Branch 2: Pure DOM inspection / observe request
        if ((promptLower.contains("inspect") || promptLower.contains("observe") || promptLower.contains("tree")) &&
            !promptLower.contains("navigate") && !promptLower.contains("search") && !promptLower.contains("http")
        ) {
            onThinkingChanged(true)
            onThoughtUpdate("Inspecting current webpage DOM hierarchy and extracting interactive element references.")
            delay(500)
            onThinkingChanged(false)

            val obsId = UUID.randomUUID().toString()
            val obsArgs = buildJsonObject {
                put("include_screenshot", false)
                put("include_compact_text", true)
                put("include_offscreen_content", false)
            }
            onToolStarted(obsId, "browser_observe", obsArgs.toString())
            delay(400)

            val result = dispatcher.invoke("browser_observe", obsArgs)
            if (result.success) {
                onToolCompleted(obsId, "browser_observe", result.output.toString())
            } else {
                onToolFailed(obsId, "browser_observe", result.output.toString())
            }

            val current = session.state.value
            return "Inspected current webpage at **${current.url ?: "active session"}** (revision ${current.revision.value}). DOM tree observation captured successfully."
        }

        // Branch 3: Navigation flow
        onThinkingChanged(true)
        val targetUrl = when {
            promptLower.contains("duckduckgo") -> "https://duckduckgo.com"
            promptLower.contains("google") -> "https://www.google.com"
            promptLower.contains("github") -> "https://github.com"
            promptLower.contains("http://") || promptLower.contains("https://") -> {
                userPrompt.split(" ", "\n").firstOrNull { it.startsWith("http", ignoreCase = true) } ?: "https://example.com"
            }
            promptLower.contains("wiki") -> "https://en.wikipedia.org/wiki/Kotlin_(programming_language)"
            else -> "https://en.wikipedia.org/wiki/Kotlin_(programming_language)"
        }

        onThoughtUpdate("Determined navigation target: $targetUrl based on user prompt. Dispatching browser_navigate.")
        delay(600)
        onThinkingChanged(false)

        // Step 1: Navigate
        val navId = UUID.randomUUID().toString()
        val navArgs = buildJsonObject { put("url", targetUrl) }
        onToolStarted(navId, "browser_navigate", navArgs.toString())
        delay(400)

        val navResult = dispatcher.invoke("browser_navigate", navArgs)
        if (navResult.success) {
            onToolCompleted(navId, "browser_navigate", navResult.output.toString())
        } else {
            onToolFailed(navId, "browser_navigate", navResult.output.toString())
            return "Failed to navigate to $targetUrl: ${navResult.output}"
        }

        // Step 2: Observe page
        onThinkingChanged(true)
        onThoughtUpdate("Navigation completed. Capturing semantic tree observation with stable element references.")
        delay(500)
        onThinkingChanged(false)

        val obsId = UUID.randomUUID().toString()
        val obsArgs = buildJsonObject {
            put("include_screenshot", false)
            put("include_compact_text", true)
            put("include_offscreen_content", false)
        }
        onToolStarted(obsId, "browser_observe", obsArgs.toString())
        delay(400)

        val obsResult = dispatcher.invoke("browser_observe", obsArgs)
        if (obsResult.success) {
            onToolCompleted(obsId, "browser_observe", obsResult.output.toString())
        } else {
            onToolFailed(obsId, "browser_observe", obsResult.output.toString())
        }

        // Step 3: Conclude reasoning
        onThinkingChanged(true)
        val currentState = session.state.value
        val finalUrl = currentState.url?.ifBlank { targetUrl } ?: targetUrl
        onThoughtUpdate("Synthesizing findings: page loaded at $finalUrl with revision ${currentState.revision.value}.")
        delay(500)
        onThinkingChanged(false)

        return "Successfully navigated to **$finalUrl** and inspected the DOM tree. The webpage is ready at observation revision ${currentState.revision.value}."
    }
}
