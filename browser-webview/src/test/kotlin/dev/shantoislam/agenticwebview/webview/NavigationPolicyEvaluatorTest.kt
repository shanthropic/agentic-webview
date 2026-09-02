package dev.shantoislam.agenticwebview.webview

import dev.shantoislam.agenticwebview.api.HostRule
import dev.shantoislam.agenticwebview.api.NavigationPolicy
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyEvaluatorTest {
    @Test
    fun allowsConfiguredDomainAndItsSubdomains() {
        val policy = NavigationPolicy(
            allowedHosts = setOf(HostRule.DomainAndSubdomains("example.com")),
        )

        assertTrue(NavigationPolicyEvaluator.evaluate("https://example.com/path", policy) is NavigationDecision.Allow)
        assertTrue(NavigationPolicyEvaluator.evaluate("https://app.example.com/path", policy) is NavigationDecision.Allow)
    }

    @Test
    fun doesNotAllowSuffixConfusion() {
        val policy = NavigationPolicy(
            allowedHosts = setOf(HostRule.DomainAndSubdomains("example.com")),
        )

        assertTrue(NavigationPolicyEvaluator.evaluate("https://evilexample.com", policy) is NavigationDecision.Block)
    }

    @Test
    fun deniedRuleTakesPrecedence() {
        val policy = NavigationPolicy(
            allowedHosts = setOf(HostRule.DomainAndSubdomains("example.com")),
            deniedHosts = setOf(HostRule.Exact("private.example.com")),
        )

        assertTrue(NavigationPolicyEvaluator.evaluate("https://private.example.com", policy) is NavigationDecision.Block)
    }

    @Test
    fun rejectsExternalSchemesByDefault() {
        assertTrue(
            NavigationPolicyEvaluator.evaluate("intent://example.com", NavigationPolicy()) is NavigationDecision.Block,
        )
    }

    @Test
    fun rejectsPlainHttpByDefault() {
        assertTrue(
            NavigationPolicyEvaluator.evaluate("http://example.com", NavigationPolicy()) is NavigationDecision.Block,
        )
    }

    @Test
    fun rejectsEmbeddedUrlCredentials() {
        assertTrue(
            NavigationPolicyEvaluator.evaluate(
                "https://user:password@example.com/private",
                NavigationPolicy(),
            ) is NavigationDecision.Block,
        )
    }
}
