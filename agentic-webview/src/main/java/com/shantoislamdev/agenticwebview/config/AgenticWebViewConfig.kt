package com.shantoislamdev.agenticwebview.config

data class AgenticWebViewConfig(
    val jsEvaluationTimeoutMs: Long = 5_000L,
    val pageSettleTimeoutMs: Long = 10_000L,
    val pageSettleDebounceMs: Long = 500L,
    val screenshotEnabled: Boolean = true,
    val screenshotQuality: Int = 75,
    val screenshotMaxDimension: Int = 1920,
    val maxDomElements: Int = 500,
    val domMutationThrottleMs: Long = 300L,
    val actionRetryCount: Int = 2,
    val enableDebugLogging: Boolean = false,
    val userAgent: String? = null,
    val allowedHosts: Set<String>? = null
) {
    class Builder {
        private var jsEvaluationTimeoutMs: Long = 5_000L
        private var pageSettleTimeoutMs: Long = 10_000L
        private var pageSettleDebounceMs: Long = 500L
        private var screenshotEnabled: Boolean = true
        private var screenshotQuality: Int = 75
        private var screenshotMaxDimension: Int = 1920
        private var maxDomElements: Int = 500
        private var domMutationThrottleMs: Long = 300L
        private var actionRetryCount: Int = 2
        private var enableDebugLogging: Boolean = false
        private var userAgent: String? = null
        private var allowedHosts: Set<String>? = null

        fun setJsEvaluationTimeoutMs(timeout: Long) = apply { this.jsEvaluationTimeoutMs = timeout }
        fun setPageSettleTimeoutMs(timeout: Long) = apply { this.pageSettleTimeoutMs = timeout }
        fun setPageSettleDebounceMs(debounce: Long) = apply { this.pageSettleDebounceMs = debounce }
        fun setScreenshotEnabled(enabled: Boolean) = apply { this.screenshotEnabled = enabled }
        fun setScreenshotQuality(quality: Int) = apply { this.screenshotQuality = quality }
        fun setScreenshotMaxDimension(dimension: Int) = apply { this.screenshotMaxDimension = dimension }
        fun setMaxDomElements(max: Int) = apply { this.maxDomElements = max }
        fun setDomMutationThrottleMs(throttle: Long) = apply { this.domMutationThrottleMs = throttle }
        fun setActionRetryCount(count: Int) = apply { this.actionRetryCount = count }
        fun setEnableDebugLogging(enabled: Boolean) = apply { this.enableDebugLogging = enabled }
        fun setUserAgent(userAgent: String?) = apply { this.userAgent = userAgent }
        fun setAllowedHosts(hosts: Set<String>?) = apply { this.allowedHosts = hosts }

        fun build() = AgenticWebViewConfig(
            jsEvaluationTimeoutMs, pageSettleTimeoutMs, pageSettleDebounceMs,
            screenshotEnabled, screenshotQuality, screenshotMaxDimension,
            maxDomElements, domMutationThrottleMs, actionRetryCount,
            enableDebugLogging, userAgent, allowedHosts
        )
    }
}
