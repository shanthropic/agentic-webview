package dev.shantoislam.agenticwebview

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.shantoislam.agenticwebview.config.AgenticWebViewConfig
import dev.shantoislam.agenticwebview.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ControllerCoreTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val serverRule = MockWebServerRule()

    @Test
    fun testCaptureState() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <button id="btn">Click Me</button>
            </body>
            </html>
        """.trimIndent()
        serverRule.enqueueHtml(html)

        val config = AgenticWebViewConfig(enableDebugLogging = true)
        val controller = AgenticWebController(config)

        composeTestRule.setContent {
            AgenticWebViewComposable(controller = controller, config = config)
        }

        // Navigate to the test page
        val navResult = controller.executeAction(AgentAction.Navigate(serverRule.url("/")))
        assertTrue(navResult is AgentResult.Success)

        // Capture state
        val stateResult = controller.captureState()
        assertTrue(stateResult is AgentResult.Success)
        val state = (stateResult as AgentResult.Success).data
        
        assertTrue(state.accessibilityTree.contains("Click Me"))
        assertEquals(serverRule.url("/"), state.url)
        assertNotNull(state.screenshotBase64)
    }

    @Test
    fun testClickAction() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <button id="btn" onclick="this.innerText='Clicked'">Click Me</button>
            </body>
            </html>
        """.trimIndent()
        serverRule.enqueueHtml(html)

        val config = AgenticWebViewConfig(enableDebugLogging = true)
        val controller = AgenticWebController(config)

        composeTestRule.setContent {
            AgenticWebViewComposable(controller = controller, config = config)
        }

        controller.executeAction(AgentAction.Navigate(serverRule.url("/")))
        
        val state = (controller.captureState() as AgentResult.Success).data
        // Find the button agentId (it should be "1" since it's the first interactive element)
        val agentId = "1" 

        val clickResult = controller.executeAction(AgentAction.Click(agentId))
        assertTrue(clickResult is AgentResult.Success)

        // Wait a bit and check state again
        Thread.sleep(500)
        val newState = (controller.captureState() as AgentResult.Success).data
        assertTrue(newState.accessibilityTree.contains("Clicked"))
    }
}
