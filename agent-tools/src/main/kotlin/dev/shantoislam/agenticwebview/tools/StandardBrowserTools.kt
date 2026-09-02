package dev.shantoislam.agenticwebview.tools

import dev.shantoislam.agenticwebview.api.AgenticBrowserSession
import dev.shantoislam.agenticwebview.api.BrowserCommand
import dev.shantoislam.agenticwebview.api.BrowserError
import dev.shantoislam.agenticwebview.api.BrowserObservation
import dev.shantoislam.agenticwebview.api.BrowserResult
import dev.shantoislam.agenticwebview.api.CommandReceipt
import dev.shantoislam.agenticwebview.api.ElementRef
import dev.shantoislam.agenticwebview.api.HistoryNavigationRequest
import dev.shantoislam.agenticwebview.api.KeyChord
import dev.shantoislam.agenticwebview.api.NavigationOperation
import dev.shantoislam.agenticwebview.api.NavigationReceipt
import dev.shantoislam.agenticwebview.api.NavigationRequest
import dev.shantoislam.agenticwebview.api.ObservationOptions
import dev.shantoislam.agenticwebview.api.ScrollAlignment
import dev.shantoislam.agenticwebview.api.ScrollDelta
import dev.shantoislam.agenticwebview.api.ScrollTarget
import dev.shantoislam.agenticwebview.api.SelectOptionMatcher
import dev.shantoislam.agenticwebview.api.TextInputMode
import java.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class BrowserToolProfile {
    MINIMAL,
    STANDARD,
    ADVANCED,
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class StandardBrowserTools(
    private val session: AgenticBrowserSession,
    profile: BrowserToolProfile = BrowserToolProfile.STANDARD,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
        classDiscriminator = "type"
    },
) : AgentToolDispatcher {

    override val definitions: List<AgentToolDefinition> = ALL_DEFINITIONS.filter { definition ->
        definition.name in when (profile) {
            BrowserToolProfile.MINIMAL -> MINIMAL_TOOL_NAMES
            BrowserToolProfile.STANDARD -> STANDARD_TOOL_NAMES
            BrowserToolProfile.ADVANCED -> ADVANCED_TOOL_NAMES
        }
    }

    override suspend fun invoke(name: String, arguments: JsonObject): AgentToolInvocationResult {
        if (definitions.none { it.name == name }) {
            return AgentToolInvocationResult(
                success = false,
                output = errorOutput("TOOL_NOT_FOUND", "Unknown or unavailable browser tool: $name"),
            )
        }
        return try {
            when (name) {
                OBSERVE -> invokeObserve(arguments)
                NAVIGATE -> encodeResult(
                    session.navigate(NavigationRequest(arguments.requiredString("url"))),
                    NavigationReceipt.serializer(),
                    summary = "Navigation completed",
                    recommendObservation = true,
                )
                CLICK -> encodeCommand(BrowserCommand.Click(arguments.requiredElementRef("target")))
                TYPE_TEXT -> invokeTypeText(arguments)
                SELECT_OPTION -> invokeSelectOption(arguments)
                SCROLL -> invokeScroll(arguments)
                PRESS_KEYS -> invokePressKeys(arguments)
                GO_BACK -> invokeHistory(NavigationOperation.BACK)
                GO_FORWARD -> invokeHistory(NavigationOperation.FORWARD)
                RELOAD -> invokeHistory(NavigationOperation.RELOAD)
                LONG_PRESS -> encodeCommand(
                    BrowserCommand.LongPress(
                        target = arguments.requiredElementRef("target"),
                        durationMs = arguments.optionalLong("duration_ms") ?: 500,
                    ),
                )
                SCROLL_INTO_VIEW -> encodeCommand(
                    BrowserCommand.ScrollIntoView(
                        target = arguments.requiredElementRef("target"),
                        alignment = arguments.optionalEnum("alignment", ScrollAlignment.entries)
                            ?: ScrollAlignment.CENTER,
                    ),
                )
                else -> error("Tool definition and dispatch table are inconsistent")
            }
        } catch (error: InvalidToolArguments) {
            AgentToolInvocationResult(false, errorOutput("INVALID_ARGUMENTS", error.message ?: "Invalid tool arguments"))
        } catch (error: SerializationException) {
            AgentToolInvocationResult(false, errorOutput("INVALID_ARGUMENTS", error.message ?: "Invalid tool arguments"))
        } catch (error: IllegalArgumentException) {
            AgentToolInvocationResult(false, errorOutput("INVALID_ARGUMENTS", error.message ?: "Invalid tool arguments"))
        }
    }

    private suspend fun invokeObserve(arguments: JsonObject): AgentToolInvocationResult {
        val result = session.observe(
            ObservationOptions(
                includeScreenshot = arguments.optionalBoolean("include_screenshot") ?: false,
                includeOffscreenContent = arguments.optionalBoolean("include_offscreen_content") ?: false,
                includeCompactText = arguments.optionalBoolean("include_compact_text") ?: true,
            ),
        )
        return encodeResult(
            result,
            BrowserObservation.serializer(),
            summary = "Browser observation captured",
            recommendObservation = false,
            valueEncoder = ::encodeObservation,
        )
    }

    private fun encodeObservation(observation: BrowserObservation): JsonElement {
        val screenshot = observation.screenshot
        val encoded = json.encodeToJsonElement(
            BrowserObservation.serializer(),
            if (screenshot == null) observation else observation.copy(screenshot = null),
        ).jsonObject
        if (screenshot == null) return encoded
        return JsonObject(
            encoded + ("screenshot" to buildJsonObject {
                put("dataBase64", Base64.getEncoder().encodeToString(screenshot.bytes))
                put("mimeType", screenshot.mimeType)
                put("widthPx", screenshot.widthPx)
                put("heightPx", screenshot.heightPx)
            }),
        )
    }

    private suspend fun invokeTypeText(arguments: JsonObject): AgentToolInvocationResult {
        val mode = arguments.optionalEnum("mode", TextInputMode.entries) ?: TextInputMode.REPLACE_ALL
        return encodeCommand(
            BrowserCommand.TypeText(
                target = arguments.requiredElementRef("target"),
                text = arguments.requiredString("text", allowEmpty = true),
                mode = mode,
            ),
        )
    }

    private suspend fun invokeSelectOption(arguments: JsonObject): AgentToolInvocationResult {
        val matchers = listOfNotNull(
            arguments.optionalString("value")?.let(SelectOptionMatcher::Value),
            arguments.optionalString("label")?.let(SelectOptionMatcher::Label),
            arguments.optionalInt("index")?.let(SelectOptionMatcher::Index),
        )
        if (matchers.size != 1) {
            throw InvalidToolArguments("Exactly one of 'value', 'label', or 'index' is required")
        }
        return encodeCommand(
            BrowserCommand.SelectOption(
                target = arguments.requiredElementRef("target"),
                option = matchers.single(),
            ),
        )
    }

    private suspend fun invokeScroll(arguments: JsonObject): AgentToolInvocationResult {
        val x = arguments.optionalDouble("delta_x_css_px") ?: 0.0
        val y = arguments.optionalDouble("delta_y_css_px") ?: 0.0
        if (x == 0.0 && y == 0.0) {
            throw InvalidToolArguments("At least one scroll delta must be non-zero")
        }
        val target = arguments["target"]?.let { ScrollTarget.Element(json.decodeFromJsonElement(it)) }
            ?: ScrollTarget.Page
        return encodeCommand(BrowserCommand.Scroll(target, ScrollDelta(x, y)))
    }

    private suspend fun invokePressKeys(arguments: JsonObject): AgentToolInvocationResult = encodeCommand(
        BrowserCommand.PressKeys(
            KeyChord(
                key = arguments.requiredString("key"),
                control = arguments.optionalBoolean("control") ?: false,
                alt = arguments.optionalBoolean("alt") ?: false,
                shift = arguments.optionalBoolean("shift") ?: false,
                meta = arguments.optionalBoolean("meta") ?: false,
            ),
        ),
    )

    private suspend fun invokeHistory(operation: NavigationOperation): AgentToolInvocationResult = encodeResult(
        session.navigateHistory(HistoryNavigationRequest(operation)),
        NavigationReceipt.serializer(),
        summary = "History navigation completed",
        recommendObservation = true,
    )

    private suspend fun encodeCommand(command: BrowserCommand): AgentToolInvocationResult = encodeResult(
        session.execute(command),
        CommandReceipt.serializer(),
        summary = "Browser action completed",
        recommendObservation = true,
    )

    private fun <T> encodeResult(
        result: BrowserResult<T>,
        serializer: KSerializer<T>,
        summary: String,
        recommendObservation: Boolean,
        valueEncoder: ((T) -> JsonElement)? = null,
    ): AgentToolInvocationResult {
        val currentState = session.state.value
        return when (result) {
            is BrowserResult.Success -> AgentToolInvocationResult(
                success = true,
                output = buildJsonObject {
                    put("status", "success")
                    put("summary", summary)
                    put("value", valueEncoder?.invoke(result.value) ?: json.encodeToJsonElement(serializer, result.value))
                    put("recommendObservation", recommendObservation)
                    putJsonObject("context") {
                        currentState.documentId?.let { put("documentId", it.value) }
                        put("revision", currentState.revision.value)
                    }
                    put("diagnostics", json.encodeToJsonElement(result.diagnostics))
                },
            )
            is BrowserResult.Failure -> {
                val encodedError = json.encodeToJsonElement(BrowserError.serializer(), result.error)
                val code = (encodedError as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull
                    ?.uppercase()
                    ?: "BROWSER_ERROR"
                AgentToolInvocationResult(
                    success = false,
                    output = buildJsonObject {
                        put("status", "error")
                        put("code", code)
                        put("summary", result.error.message)
                        put("error", encodedError)
                        put("recommendObservation", shouldRecommendObservation(result.error))
                        putJsonObject("context") {
                            currentState.documentId?.let { put("documentId", it.value) }
                            put("revision", currentState.revision.value)
                        }
                        put("diagnostics", json.encodeToJsonElement(result.diagnostics))
                    },
                )
            }
        }
    }

    private fun shouldRecommendObservation(error: BrowserError): Boolean = when (error) {
        is BrowserError.StaleElementReference,
        is BrowserError.ElementNotFound,
        is BrowserError.ElementNotActionable,
        is BrowserError.ElementOccluded,
        is BrowserError.PageNotReady -> true
        else -> false
    }

    private fun JsonObject.requiredElementRef(name: String): ElementRef {
        val value = this[name] ?: throw InvalidToolArguments("Missing required argument: $name")
        return json.decodeFromJsonElement(value)
    }

    private fun JsonObject.requiredString(name: String, allowEmpty: Boolean = false): String {
        val value = optionalString(name)
            ?: throw InvalidToolArguments("Missing or invalid string argument: $name")
        if (!allowEmpty && value.isBlank()) throw InvalidToolArguments("Argument '$name' must not be blank")
        return value
    }

    private fun JsonObject.optionalString(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.optionalBoolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.optionalInt(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

    private fun JsonObject.optionalLong(name: String): Long? = this[name]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    private fun JsonObject.optionalDouble(name: String): Double? = this[name]?.jsonPrimitive?.doubleOrNull

    private fun <T : Enum<T>> JsonObject.optionalEnum(name: String, values: List<T>): T? {
        val raw = this[name]?.jsonPrimitive?.contentOrNull ?: return null
        return values.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: throw InvalidToolArguments("Unknown value for '$name': $raw")
    }

    private companion object {
        const val OBSERVE = "browser_observe"
        const val NAVIGATE = "browser_navigate"
        const val CLICK = "browser_click"
        const val TYPE_TEXT = "browser_type_text"
        const val SELECT_OPTION = "browser_select_option"
        const val SCROLL = "browser_scroll"
        const val PRESS_KEYS = "browser_press_keys"
        const val GO_BACK = "browser_go_back"
        const val GO_FORWARD = "browser_go_forward"
        const val RELOAD = "browser_reload"
        const val LONG_PRESS = "browser_long_press"
        const val SCROLL_INTO_VIEW = "browser_scroll_into_view"

        val MINIMAL_TOOL_NAMES = setOf(OBSERVE, NAVIGATE, CLICK, TYPE_TEXT)
        val STANDARD_TOOL_NAMES = MINIMAL_TOOL_NAMES + setOf(
            SELECT_OPTION,
            SCROLL,
            PRESS_KEYS,
            GO_BACK,
            GO_FORWARD,
            RELOAD,
        )
        val ADVANCED_TOOL_NAMES = STANDARD_TOOL_NAMES + setOf(LONG_PRESS, SCROLL_INTO_VIEW)

        val ALL_DEFINITIONS = listOf(
            tool(
                OBSERVE,
                "Observe the current webpage and return semantic content with stable element references.",
                buildJsonObject {
                    booleanProperty("include_screenshot")
                    booleanProperty("include_compact_text")
                    booleanProperty("include_offscreen_content")
                },
            ),
            tool(
                NAVIGATE,
                "Navigate to an HTTP or HTTPS URL allowed by the host policy.",
                buildJsonObject { stringProperty("url", minLength = 1) },
                listOf("url"),
            ),
            tool(CLICK, "Click an actionable element from the latest observation.", targetProperties(), listOf("target")),
            tool(
                TYPE_TEXT,
                "Enter text into an editable element from the latest observation.",
                buildJsonObject {
                    put("target", elementReferenceSchema())
                    stringProperty("text")
                    enumProperty("mode", TextInputMode.entries.map { it.name.lowercase() }, "replace_all")
                },
                listOf("target", "text"),
            ),
            tool(
                SELECT_OPTION,
                "Select exactly one option by value, visible label, or zero-based index.",
                buildJsonObject {
                    put("target", elementReferenceSchema())
                    stringProperty("value")
                    stringProperty("label")
                    integerProperty("index", minimum = 0)
                },
                listOf("target"),
                exactlyOneOf = listOf("value", "label", "index"),
            ),
            tool(
                SCROLL,
                "Scroll the page or a referenced scroll container by CSS pixels.",
                buildJsonObject {
                    put("target", elementReferenceSchema())
                    numberProperty("delta_x_css_px")
                    numberProperty("delta_y_css_px")
                },
            ),
            tool(
                PRESS_KEYS,
                "Dispatch a keyboard chord to the focused webpage element.",
                buildJsonObject {
                    stringProperty("key", minLength = 1)
                    booleanProperty("control")
                    booleanProperty("alt")
                    booleanProperty("shift")
                    booleanProperty("meta")
                },
                listOf("key"),
            ),
            emptyTool(GO_BACK, "Navigate to the previous browser history entry."),
            emptyTool(GO_FORWARD, "Navigate to the next browser history entry."),
            emptyTool(RELOAD, "Reload the current webpage."),
            tool(
                LONG_PRESS,
                "Long-press an actionable element.",
                buildJsonObject {
                    put("target", elementReferenceSchema())
                    integerProperty("duration_ms", minimum = 1, maximum = 60_000)
                },
                listOf("target"),
            ),
            tool(
                SCROLL_INTO_VIEW,
                "Bring a referenced element into the viewport.",
                buildJsonObject {
                    put("target", elementReferenceSchema())
                    enumProperty("alignment", ScrollAlignment.entries.map { it.name.lowercase() }, "center")
                },
                listOf("target"),
            ),
        )

        fun tool(
            name: String,
            description: String,
            properties: JsonObject,
            required: List<String> = emptyList(),
            exactlyOneOf: List<String> = emptyList(),
        ) = AgentToolDefinition(
            name = name,
            description = description,
            inputSchema = buildJsonObject {
                put("type", "object")
                put("additionalProperties", false)
                put("properties", properties)
                if (required.isNotEmpty()) {
                    put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
                }
                if (exactlyOneOf.isNotEmpty()) {
                    putJsonArray("oneOf") {
                        exactlyOneOf.forEach { field ->
                            add(buildJsonObject {
                                put("required", buildJsonArray { add(JsonPrimitive(field)) })
                            })
                        }
                    }
                }
            },
            outputSchema = toolOutputSchema(),
        )

        fun emptyTool(name: String, description: String): AgentToolDefinition =
            tool(name, description, buildJsonObject { })

        fun targetProperties(): JsonObject = buildJsonObject { put("target", elementReferenceSchema()) }

        fun elementReferenceSchema(): JsonObject = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            putJsonObject("properties") {
                stringProperty("documentId", minLength = 1)
                stringProperty("frameId", minLength = 1)
                stringProperty("elementId", minLength = 1)
                integerProperty("observedAtRevision", minimum = 0)
            }
            put("required", buildJsonArray {
                listOf("documentId", "frameId", "elementId", "observedAtRevision")
                    .forEach { add(JsonPrimitive(it)) }
            })
        }

        fun toolOutputSchema(): JsonObject = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                enumProperty("status", listOf("success", "error"))
                stringProperty("summary")
                booleanProperty("recommendObservation")
                putJsonObject("context") { put("type", "object") }
            }
            put("required", buildJsonArray {
                listOf("status", "summary", "recommendObservation", "context")
                    .forEach { add(JsonPrimitive(it)) }
            })
        }

        fun JsonObjectBuilderScope.stringProperty(name: String, minLength: Int? = null) =
            putJsonObject(name) {
                put("type", "string")
                minLength?.let { put("minLength", it) }
            }

        fun JsonObjectBuilderScope.booleanProperty(name: String) =
            putJsonObject(name) { put("type", "boolean") }

        fun JsonObjectBuilderScope.numberProperty(name: String) =
            putJsonObject(name) { put("type", "number") }

        fun JsonObjectBuilderScope.integerProperty(
            name: String,
            minimum: Int? = null,
            maximum: Int? = null,
        ) = putJsonObject(name) {
            put("type", "integer")
            minimum?.let { put("minimum", it) }
            maximum?.let { put("maximum", it) }
        }

        fun JsonObjectBuilderScope.enumProperty(
            name: String,
            values: List<String>,
            default: String? = null,
        ) = putJsonObject(name) {
            put("type", "string")
            putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
            default?.let { put("default", it) }
        }

        fun errorOutput(code: String, message: String): JsonObject = buildJsonObject {
            put("status", "error")
            put("code", code)
            put("summary", message)
            put("recommendObservation", false)
            putJsonObject("context") { }
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }
    }
}

private typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder

private class InvalidToolArguments(message: String) : IllegalArgumentException(message)
