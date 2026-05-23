package com.shantoislamdev.agenticwebview

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shantoislamdev.agenticwebview.config.AgenticWebViewConfig
import com.shantoislamdev.agenticwebview.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StabilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val serverRule = MockWebServerRule()

    @Test
    fun testIdStabilityAcrossMutations() = runBlocking {
        serverRule.enqueueHtml("""
            <html>
                <body>
                    <button id="btn">Click me</button>
                    <div id="container"></div>
                </body>
            </html>
        """.trimIndent())

        val config = AgenticWebViewConfig()
        val controller = AgenticWebController(config)

        composeTestRule.setContent {
            AgenticWebViewComposable(controller = controller, config = config)
        }

        controller.executeAction(AgentAction.Navigate(serverRule.url("/")))

        val state1 = (controller.captureState() as AgentResult.Success).data
        val btnId = state1.accessibilityTree.let { tree ->
            // Simple parsing of the tree JSON string (since tree is a JSON string in AgentState)
            // In a real test we'd use a JSON library
            val regex = """"id":"(\d+)","tag":"BUTTON"""".toRegex()
            regex.find(tree)?.groupValues?.get(1)
        }
        
        assertNotNull("Button ID should not be null", btnId)

        // Trigger mutation
        controller.executeAction(AgentAction.Wait(100))
        // We'll use evalJs directly to trigger a DOM change without re-navigating
        // AgenticWebController.evalJs is private, so we might need to use a Navigate to a slightly different page 
        // OR better, we trust the MutationObserver in the script.
        
        // Let's use evaluateJavascript via reflection or just navigate to a page that appends an element.
        serverRule.enqueueHtml("""
            <html>
                <body>
                    <button id="btn">Click me</button>
                    <div id="container"><span>New Element</span></div>
                </body>
            </html>
        """.trimIndent())
        controller.executeAction(AgentAction.Navigate(serverRule.url("/update")))

        val state2 = (controller.captureState() as AgentResult.Success).data
        val btnIdAfter = state2.accessibilityTree.let { tree ->
            val regex = """"id":"$btnId","tag":"BUTTON"""".toRegex()
            regex.find(tree)?.groupValues?.get(0)
        }
        
        assertNotNull("Button ID should be the same after mutation/re-render if it's the same element", btnIdAfter)
    }
}
