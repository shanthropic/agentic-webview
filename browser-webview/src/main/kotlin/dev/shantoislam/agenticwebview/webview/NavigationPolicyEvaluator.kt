package dev.shantoislam.agenticwebview.webview

import dev.shantoislam.agenticwebview.api.NavigationPolicy
import java.net.URI

internal object NavigationPolicyEvaluator {
    fun evaluate(url: String, policy: NavigationPolicy): NavigationDecision {
        val uri = try {
            URI(url)
        } catch (error: Exception) {
            return NavigationDecision.Block("Malformed URL: ${error.message ?: "invalid syntax"}")
        }

        val scheme = uri.scheme?.lowercase()
            ?: return NavigationDecision.Block("URL has no scheme")
        if (scheme !in policy.allowedSchemes) {
            return NavigationDecision.Block("Scheme '$scheme' is not allowed")
        }
        if (uri.rawUserInfo != null) {
            return NavigationDecision.Block("URLs containing embedded credentials are not allowed")
        }

        val host = uri.host
            ?: return NavigationDecision.Block("URL has no valid host")
        if (policy.deniedHosts.any { it.matches(host) }) {
            return NavigationDecision.Block("Host '$host' is denied")
        }
        if (policy.allowedHosts.isNotEmpty() && policy.allowedHosts.none { it.matches(host) }) {
            return NavigationDecision.Block("Host '$host' is not allowed")
        }
        return NavigationDecision.Allow
    }
}

internal sealed interface NavigationDecision {
    data object Allow : NavigationDecision
    data class Block(val reason: String) : NavigationDecision
}
