package dev.shantoislam.agenticwebview.app.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import dev.shantoislam.agenticwebview.tools.AgentToolDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Koog v1.2.0 ToolSet wrapping AgentToolDispatcher (StandardBrowserTools).
 */
class BrowserAgentTools(
    private val dispatcher: AgentToolDispatcher,
    private val json: Json = Json { prettyPrint = true },
) : ToolSet {

    @Tool
    @LLMDescription("Observe the current webpage and return semantic content with stable element references.")
    suspend fun browser_observe(
        @LLMDescription("Include base64 screenshot") includeScreenshot: Boolean = false,
        @LLMDescription("Include compact text representation") includeCompactText: Boolean = true,
        @LLMDescription("Include offscreen content") includeOffscreenContent: Boolean = false,
    ): String = invoke("browser_observe", buildJsonObject {
        put("include_screenshot", includeScreenshot)
        put("include_compact_text", includeCompactText)
        put("include_offscreen_content", includeOffscreenContent)
    })

    @Tool
    @LLMDescription("Navigate to an HTTP or HTTPS URL.")
    suspend fun browser_navigate(
        @LLMDescription("Absolute HTTP or HTTPS URL") url: String,
    ): String =
        invoke("browser_navigate", buildJsonObject { put("url", url) })

    @Tool
    @LLMDescription("Click an element reference returned by browser_observe.")
    suspend fun browser_click(
        @LLMDescription("Element documentId") documentId: String,
        @LLMDescription("Element frameId") frameId: String,
        @LLMDescription("Element elementId") elementId: String,
        @LLMDescription("Element observedAtRevision") observedAtRevision: Long,
    ): String = invokeWithTarget("browser_click", documentId, frameId, elementId, observedAtRevision)

    @Tool
    @LLMDescription("Enter text into an editable element reference returned by browser_observe.")
    suspend fun browser_type_text(
        @LLMDescription("Element documentId") documentId: String,
        @LLMDescription("Element frameId") frameId: String,
        @LLMDescription("Element elementId") elementId: String,
        @LLMDescription("Element observedAtRevision") observedAtRevision: Long,
        @LLMDescription("Text to enter") text: String,
        @LLMDescription("replace_all, append, insert_at_selection, or clear") mode: String = "replace_all",
    ): String = invokeWithTarget("browser_type_text", documentId, frameId, elementId, observedAtRevision) {
        put("text", text)
        put("mode", mode)
    }

    @Tool
    @LLMDescription("Select an option in a dropdown by value, label, or index.")
    suspend fun browser_select_option(
        @LLMDescription("Element documentId") documentId: String,
        @LLMDescription("Element frameId") frameId: String,
        @LLMDescription("Element elementId") elementId: String,
        @LLMDescription("Element observedAtRevision") observedAtRevision: Long,
        @LLMDescription("value, label, or index") matcherType: String,
        @LLMDescription("Matcher value; integer when matcherType is index") matcherValue: String,
    ): String = invokeWithTarget("browser_select_option", documentId, frameId, elementId, observedAtRevision) {
        when (matcherType.trim().lowercase()) {
            "value" -> put("value", matcherValue)
            "label" -> put("label", matcherValue)
            "index" -> put("index", matcherValue.trim().toIntOrNull() ?: -1)
            else -> put(matcherType.trim(), matcherValue)
        }
    }

    @Tool
    @LLMDescription("Scroll the page by CSS pixels.")
    suspend fun browser_scroll(
        @LLMDescription("Vertical delta in CSS pixels") deltaYCssPx: Double,
        @LLMDescription("Horizontal delta in CSS pixels") deltaXCssPx: Double = 0.0,
    ): String =
        invoke("browser_scroll", buildJsonObject {
            put("delta_x_css_px", deltaXCssPx)
            put("delta_y_css_px", deltaYCssPx)
        })

    @Tool
    @LLMDescription("Dispatch a keyboard chord to the focused webpage element.")
    suspend fun browser_press_keys(
        @LLMDescription("KeyboardEvent key value (e.g. Enter, Escape, ArrowDown)") key: String,
        @LLMDescription("Hold Control") control: Boolean = false,
        @LLMDescription("Hold Alt") alt: Boolean = false,
        @LLMDescription("Hold Shift") shift: Boolean = false,
        @LLMDescription("Hold Meta or Command") meta: Boolean = false,
    ): String = invoke("browser_press_keys", buildJsonObject {
        put("key", key)
        put("control", control)
        put("alt", alt)
        put("shift", shift)
        put("meta", meta)
    })

    @Tool
    @LLMDescription("Navigate to the previous browser history entry.")
    suspend fun browser_go_back(): String = invoke("browser_go_back")

    @Tool
    @LLMDescription("Navigate to the next browser history entry.")
    suspend fun browser_go_forward(): String = invoke("browser_go_forward")

    @Tool
    @LLMDescription("Reload the current webpage.")
    suspend fun browser_reload(): String = invoke("browser_reload")

    private suspend fun invokeWithTarget(
        name: String,
        documentId: String,
        frameId: String,
        elementId: String,
        observedAtRevision: Long,
        additional: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ): String = invoke(name, buildJsonObject {
        put("target", buildJsonObject {
            put("documentId", documentId)
            put("frameId", frameId)
            put("elementId", elementId)
            put("observedAtRevision", observedAtRevision)
        })
        additional()
    })

    private suspend fun invoke(name: String, arguments: JsonObject = buildJsonObject { }): String =
        json.encodeToString(JsonObject.serializer(), dispatcher.invoke(name, arguments).output)
}
