package dev.shantoislam.agenticwebview.api

import java.net.IDN
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class AgenticBrowserConfiguration(
    val runtime: RuntimeConfiguration = RuntimeConfiguration(),
    val observation: ObservationConfiguration = ObservationConfiguration(),
    val actions: ActionConfiguration = ActionConfiguration(),
    val navigation: NavigationPolicy = NavigationPolicy(),
    val screenshots: ScreenshotConfiguration = ScreenshotConfiguration(),
    val diagnostics: DiagnosticsConfiguration = DiagnosticsConfiguration(),
    val experimental: ExperimentalConfiguration = ExperimentalConfiguration(),
) {
    fun validationErrors(): List<String> = buildList {
        val allowed = navigation.allowedHosts.map { it.key }.toSet()
        val denied = navigation.deniedHosts.map { it.key }.toSet()
        val conflicts = allowed intersect denied
        if (conflicts.isNotEmpty()) add("Host rules cannot be both allowed and denied: ${conflicts.sorted()}")
    }

    fun requireValid(): AgenticBrowserConfiguration = apply {
        val errors = validationErrors()
        require(errors.isEmpty()) { errors.joinToString(separator = "; ") }
    }
}

@Serializable
data class RuntimeConfiguration(
    val requestTimeoutMs: Long = 5_000,
    val initializationTimeoutMs: Long = 10_000,
    val readyQuietWindowMs: Long = 100,
    val maximumPendingRequests: Int = 64,
    val maximumMessageBytes: Int = 2 * 1024 * 1024,
) {
    init {
        require(requestTimeoutMs in 1..120_000) { "requestTimeoutMs must be within 1..120000" }
        require(initializationTimeoutMs in 1..120_000) { "initializationTimeoutMs must be within 1..120000" }
        require(readyQuietWindowMs in 0..10_000) { "readyQuietWindowMs must be within 0..10000" }
        require(maximumPendingRequests in 1..1_024) { "maximumPendingRequests must be within 1..1024" }
        require(maximumMessageBytes in 1_024..16 * 1024 * 1024) { "maximumMessageBytes must be within 1024..16777216" }
    }
}

@Serializable
data class ObservationConfiguration(
    val maximumVisitedNodes: Int = 10_000,
    val maximumEmittedNodes: Int = 750,
    val maximumTotalTextCharacters: Int = 100_000,
    val maximumTextCharactersPerNode: Int = 2_000,
    val maximumTraversalMs: Long = 1_500,
    val viewportExpansionPx: Int = 0,
    val maximumFrameDepth: Int = 8,
    val maximumShadowDepth: Int = 16,
) {
    init {
        require(maximumVisitedNodes in 1..1_000_000) { "maximumVisitedNodes is out of range" }
        require(maximumEmittedNodes in 1..100_000) { "maximumEmittedNodes is out of range" }
        require(maximumEmittedNodes <= maximumVisitedNodes) { "maximumEmittedNodes cannot exceed maximumVisitedNodes" }
        require(maximumTotalTextCharacters in 1..10_000_000) { "maximumTotalTextCharacters is out of range" }
        require(maximumTextCharactersPerNode in 1..100_000) { "maximumTextCharactersPerNode is out of range" }
        require(maximumTraversalMs in 1..30_000) { "maximumTraversalMs is out of range" }
        require(viewportExpansionPx >= -1) { "viewportExpansionPx must be -1 or non-negative" }
        require(maximumFrameDepth in 0..64) { "maximumFrameDepth is out of range" }
        require(maximumShadowDepth in 0..64) { "maximumShadowDepth is out of range" }
    }
}

@Serializable
data class ActionConfiguration(
    val preparationTimeoutMs: Long = 3_000,
    val verificationTimeoutMs: Long = 3_000,
    val geometryStableCycles: Int = 2,
    val geometryTolerancePx: Double = 1.0,
    val pointerStrategy: PointerActionStrategy = PointerActionStrategy.DOM_ONLY,
) {
    init {
        require(preparationTimeoutMs in 1..120_000) { "preparationTimeoutMs is out of range" }
        require(verificationTimeoutMs in 1..120_000) { "verificationTimeoutMs is out of range" }
        require(geometryStableCycles in 1..20) { "geometryStableCycles is out of range" }
        require(geometryTolerancePx in 0.0..100.0) { "geometryTolerancePx is out of range" }
    }
}

@Serializable
enum class PointerActionStrategy { DOM_ONLY, NATIVE_PREFERRED }

@Serializable
data class ScreenshotConfiguration(
    val maximumDimensionPx: Int = 1_920,
    val jpegQuality: Int = 75,
    val maximumEncodedBytes: Int = 8 * 1024 * 1024,
    val maskCssSelectors: List<String> = emptyList(),
) {
    init {
        require(maximumDimensionPx in 1..16_384) { "maximumDimensionPx is out of range" }
        require(jpegQuality in 0..100) { "jpegQuality must be within 0..100" }
        require(maximumEncodedBytes in 1_024..64 * 1024 * 1024) { "maximumEncodedBytes is out of range" }
        require(maskCssSelectors.size <= 100) { "At most 100 screenshot mask selectors are allowed" }
        require(maskCssSelectors.all { it.isNotBlank() && it.length <= 500 }) {
            "Screenshot mask selectors must be non-blank and at most 500 characters"
        }
    }
}

@Serializable
data class DiagnosticsConfiguration(
    val enabled: Boolean = false,
    val includePageContent: Boolean = false,
)

@Serializable
data class ExperimentalConfiguration(
    val hideWebDriverProperty: Boolean = false,
    val installChromeLikeGlobals: Boolean = false,
    val forceFutureShadowRootsOpen: Boolean = false,
)

@Serializable
data class NavigationPolicy(
    val allowedSchemes: Set<String> = setOf("https"),
    val allowedHosts: Set<HostRule> = emptySet(),
    val deniedHosts: Set<HostRule> = emptySet(),
    val popupPolicy: PrivilegedRequestPolicy = PrivilegedRequestPolicy.ASK,
    val dialogPolicy: PrivilegedRequestPolicy = PrivilegedRequestPolicy.ASK,
    val downloadPolicy: PrivilegedRequestPolicy = PrivilegedRequestPolicy.ASK,
    val permissionPolicy: PrivilegedRequestPolicy = PrivilegedRequestPolicy.DENY,
    val fileChooserPolicy: PrivilegedRequestPolicy = PrivilegedRequestPolicy.ASK,
) {
    init {
        require(allowedSchemes.isNotEmpty()) { "At least one URL scheme must be allowed" }
        require(allowedSchemes.all { it.isNotBlank() && it == it.lowercase() }) {
            "Allowed schemes must be non-blank lowercase values"
        }
    }
}

@Serializable
enum class PrivilegedRequestPolicy { ALLOW, DENY, ASK }

@Serializable
sealed interface HostRule {
    val host: String
    val key: String

    fun matches(candidateHost: String): Boolean

    @Serializable
    @SerialName("exact")
    data class Exact(override val host: String) : HostRule {
        @Transient
        private val normalized = normalizeHost(host)
        @Transient
        override val key: String = "exact:$normalized"
        override fun matches(candidateHost: String): Boolean = normalizeHost(candidateHost) == normalized
    }

    @Serializable
    @SerialName("domain_and_subdomains")
    data class DomainAndSubdomains(override val host: String) : HostRule {
        @Transient
        private val normalized = normalizeHost(host)
        @Transient
        override val key: String = "domain:$normalized"
        override fun matches(candidateHost: String): Boolean {
            val candidate = normalizeHost(candidateHost)
            return candidate == normalized || candidate.endsWith(".$normalized")
        }
    }
}

private fun normalizeHost(raw: String): String {
    val trimmed = raw.trim().trimEnd('.').lowercase()
    require(trimmed.isNotEmpty()) { "Host must not be blank" }
    require('/' !in trimmed && ':' !in trimmed) { "Host rules must contain a hostname only" }
    val ascii = IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).lowercase()
    require(ascii.length <= 253) { "Host is too long" }
    return ascii
}
