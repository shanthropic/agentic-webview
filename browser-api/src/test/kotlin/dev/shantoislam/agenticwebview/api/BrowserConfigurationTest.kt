package dev.shantoislam.agenticwebview.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserConfigurationTest {
    @Test
    fun domainRuleMatchesOnlyHostBoundary() {
        val rule = HostRule.DomainAndSubdomains("Example.COM.")

        assertTrue(rule.matches("example.com"))
        assertTrue(rule.matches("a.example.com"))
        assertFalse(rule.matches("evilexample.com"))
        assertFalse(rule.matches("example.com.evil.test"))
    }

    @Test
    fun exactRuleDoesNotMatchSubdomains() {
        val rule = HostRule.Exact("example.com")

        assertTrue(rule.matches("EXAMPLE.COM"))
        assertFalse(rule.matches("www.example.com"))
    }

    @Test
    fun conflictingRulesFailValidation() {
        val configuration = AgenticBrowserConfiguration(
            navigation = NavigationPolicy(
                allowedHosts = setOf(HostRule.Exact("example.com")),
                deniedHosts = setOf(HostRule.Exact("example.com")),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) { configuration.requireValid() }
    }

    @Test
    fun invalidResourceLimitsFailAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeConfiguration(maximumPendingRequests = 0)
        }
    }

    @Test
    fun invalidScreenshotMaskSelectorsFailAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            ScreenshotConfiguration(maskCssSelectors = listOf(" "))
        }
    }

    @Test
    fun historyRequestCannotMasqueradeAsUrlNavigation() {
        assertThrows(IllegalArgumentException::class.java) {
            HistoryNavigationRequest(NavigationOperation.URL)
        }
    }
}
