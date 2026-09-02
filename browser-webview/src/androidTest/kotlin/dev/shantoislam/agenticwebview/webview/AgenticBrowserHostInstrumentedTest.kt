package dev.shantoislam.agenticwebview.webview

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.shantoislam.agenticwebview.api.AgenticBrowserConfiguration
import dev.shantoislam.agenticwebview.api.BrowserResult
import dev.shantoislam.agenticwebview.api.HostRule
import dev.shantoislam.agenticwebview.api.NavigationPolicy
import dev.shantoislam.agenticwebview.api.NavigationRequest
import dev.shantoislam.agenticwebview.api.RuntimeConfiguration
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgenticBrowserHostInstrumentedTest {
    @Test
    fun navigatesInitializesRuntimeAndObservesAnActionableElement() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(
                "<html><head><title>Fixture</title></head><body><h1>Ready</h1><button>Continue</button></body></html>",
            ),
        )
        server.start()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var host: AgenticBrowserHost
        instrumentation.runOnMainSync {
            host = AgenticBrowserHost.create(
                instrumentation.targetContext,
                AgenticBrowserConfiguration(
                    runtime = RuntimeConfiguration(requestTimeoutMs = 10_000, initializationTimeoutMs = 10_000),
                    navigation = NavigationPolicy(
                        allowedSchemes = setOf("http"),
                        allowedHosts = setOf(HostRule.Exact(server.hostName)),
                    ),
                ),
            )
        }

        try {
            val navigation = withTimeout(15_000) {
                host.session.navigate(NavigationRequest(server.url("/").toString()))
            }
            assertTrue(navigation is BrowserResult.Success)
            val observation = host.session.observe()
            assertTrue(observation is BrowserResult.Success)
            val value = (observation as BrowserResult.Success).value
            assertTrue(value.nodes.any { it.tagName == "button" && it.elementRef != null })
        } finally {
            instrumentation.runOnMainSync { host.close() }
            server.shutdown()
        }
    }
}
