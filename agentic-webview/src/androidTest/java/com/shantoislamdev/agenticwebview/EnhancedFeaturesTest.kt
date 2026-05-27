package com.shantoislamdev.agenticwebview

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shantoislamdev.agenticwebview.config.AgenticWebViewConfig
import com.shantoislamdev.agenticwebview.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnhancedFeaturesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val serverRule = MockWebServerRule()

    private fun createController(config: AgenticWebViewConfig = AgenticWebViewConfig(enableDebugLogging = true)): AgenticWebController {
        val controller = AgenticWebController(config)
        composeTestRule.setContent {
            AgenticWebViewComposable(controller = controller, config = config)
        }
        return controller
    }

    private suspend fun navigateAndLoad(controller: AgenticWebController, html: String): AgentState {
        serverRule.enqueueHtml(html)
        val navResult = controller.executeAction(AgentAction.Navigate(serverRule.url("/")))
        assertTrue("Navigation should succeed", navResult is AgentResult.Success)
        return (controller.captureState() as AgentResult.Success).data
    }

    @Test
    fun testCaptureStateIncludesSelectorMap() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html><body>
                <button id="btn">Click Me</button>
            </body></html>
        """.trimIndent()

        val controller = createController()
        val state = navigateAndLoad(controller, html)

        assertNotNull("selectorMap should be present", state.selectorMap)
        assertTrue("selectorMap should not be empty", state.selectorMap!!.isNotEmpty())
    }

    @Test
    fun testCaptureStateIncludesCompactTree() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html><body>
                <button id="btn">Submit</button>
            </body></html>
        """.trimIndent()

        val controller = createController()
        val state = navigateAndLoad(controller, html)

        assertNotNull("compactTree should be present", state.compactTree)
        assertTrue("compactTree should contain button reference", state.compactTree!!.contains("button"))
    }

    @Test
    fun testSendKeysAction() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html><body>
                <input id="inp" type="text" />
            </body></html>
        """.trimIndent()

        val controller = createController()
        navigateAndLoad(controller, html)

        val result = controller.executeAction(AgentAction.SendKeys("Enter"))
        assertTrue("SendKeys should succeed", result is AgentResult.Success)
    }

    @Test
    fun testScrollToTopAction() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html><body>
                <div style="height:3000px"><button id="btn">Bottom</button></div>
            </body></html>
        """.trimIndent()

        val controller = createController()
        navigateAndLoad(controller, html)

        val result = controller.executeAction(AgentAction.ScrollToTop())
        assertTrue("ScrollToTop should succeed", result is AgentResult.Success)
    }

    @Test
    fun testScrollToBottomAction() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html><body>
                <div style="height:3000px"><button id="btn">Bottom</button></div>
            </body></html>
        """.trimIndent()

        val controller = createController()
        navigateAndLoad(controller, html)

        val result = controller.executeAction(AgentAction.ScrollToBottom())
        assertTrue("ScrollToBottom should succeed", result is AgentResult.Success)
    }

    @Test
    fun testScrollToPercentAction() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html><body>
                <div style="height:3000px">Content</div>
            </body></html>
        """.trimIndent()

        val controller = createController()
        navigateAndLoad(controller, html)

        val result = controller.executeAction(AgentAction.ScrollToPercent(50f))
        assertTrue("ScrollToPercent should succeed", result is AgentResult.Success)
    }

    @Test
    fun testScrollToTextAction() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html><body>
                <div style="height:3000px">Tall content</div>
                <p id="target">Find this text</p>
            </body></html>
        """.trimIndent()

        val controller = createController()
        navigateAndLoad(controller, html)

        val result = controller.executeAction(AgentAction.ScrollToText("Find this text"))
        assertTrue("ScrollToText should succeed", result is AgentResult.Success)
    }

    @Test
    fun testPreviousPageAction() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html><body>
                <div style="height:3000px">Content</div>
            </body></html>
        """.trimIndent()

        val controller = createController()
        navigateAndLoad(controller, html)

        val result = controller.executeAction(AgentAction.PreviousPage())
        assertTrue("PreviousPage should succeed", result is AgentResult.Success)
    }

    @Test
    fun testNextPageAction() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html><body>
                <div style="height:3000px">Content</div>
            </body></html>
        """.trimIndent()

        val controller = createController()
        navigateAndLoad(controller, html)

        val result = controller.executeAction(AgentAction.NextPage())
        assertTrue("NextPage should succeed", result is AgentResult.Success)
    }

    @Test
    fun testSelectDropdownOptionAction() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html><body>
                <select id="sel">
                    <option value="a">Alpha</option>
                    <option value="b">Beta</option>
                    <option value="c">Charlie</option>
                </select>
            </body></html>
        """.trimIndent()

        val controller = createController()
        navigateAndLoad(controller, html)

        val result = controller.executeAction(AgentAction.SelectDropdownOption("0", "Beta"))
        assertTrue("SelectDropdownOption should succeed", result is AgentResult.Success)
    }

    @Test
    fun testGetDropdownOptionsPublicMethod() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html><body>
                <select id="sel">
                    <option value="x">X-Ray</option>
                    <option value="y">Yankee</option>
                </select>
            </body></html>
        """.trimIndent()

        val controller = createController()
        navigateAndLoad(controller, html)

        val result = controller.getDropdownOptions("0")
        assertTrue("getDropdownOptions should succeed", result is AgentResult.Success)
        val options = (result as AgentResult.Success).data
        assertTrue("Should have at least 2 options", options.size >= 2)
        assertEquals("x", options[0].value)
        assertEquals("X-Ray", options[0].text)
    }

    @Test
    fun testScrollDirectionEnum() {
        val directions = ScrollDirection.entries
        assertEquals(4, directions.size)
        assertTrue(directions.contains(ScrollDirection.UP))
        assertTrue(directions.contains(ScrollDirection.DOWN))
        assertTrue(directions.contains(ScrollDirection.LEFT))
        assertTrue(directions.contains(ScrollDirection.RIGHT))
    }

    @Test
    fun testConfigViewportExpansion() {
        val config = AgenticWebViewConfig(viewportExpansion = 100)
        assertEquals(100, config.viewportExpansion)
    }

    @Test
    fun testConfigElementStabilityTimeout() {
        val config = AgenticWebViewConfig(elementStabilityTimeoutMs = 2000)
        assertEquals(2000L, config.elementStabilityTimeoutMs)
    }

    @Test
    fun testConfigAntiDetection() {
        val config = AgenticWebViewConfig(enableAntiDetection = false)
        assertFalse(config.enableAntiDetection)
    }

    @Test
    fun testConfigBuilderNewOptions() {
        val config = AgenticWebViewConfig.Builder()
            .setViewportExpansion(200)
            .setElementStabilityTimeoutMs(1500)
            .setEnableAntiDetection(false)
            .setIncludeAttributes(listOf("id", "class"))
            .setDeniedHosts(setOf("evil.com"))
            .setHomeUrl("https://safe.com")
            .build()

        assertEquals(200, config.viewportExpansion)
        assertEquals(1500L, config.elementStabilityTimeoutMs)
        assertFalse(config.enableAntiDetection)
        assertEquals(listOf("id", "class"), config.includeAttributes)
        assertEquals(setOf("evil.com"), config.deniedHosts)
        assertEquals("https://safe.com", config.homeUrl)
    }

    @Test
    fun testScrollToTopWithAgentId() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html><body>
                <div style="height:3000px"><button id="btn">Bottom</button></div>
            </body></html>
        """.trimIndent()

        val controller = createController()
        navigateAndLoad(controller, html)

        val result = controller.executeAction(AgentAction.ScrollToTop(agentId = null))
        assertTrue("ScrollToTop with null agentId should succeed", result is AgentResult.Success)
    }

    @Test
    fun testDoneAction() = runBlocking {
        val controller = createController()
        val result = controller.executeAction(AgentAction.Done("Task complete", true))
        assertTrue("Done action should succeed", result is AgentResult.Success)
    }

    @Test
    fun testDeniedHostsConfig() {
        val config = AgenticWebViewConfig(deniedHosts = setOf("ads.com", "tracker.io"))
        assertEquals(setOf("ads.com", "tracker.io"), config.deniedHosts)
    }
}
