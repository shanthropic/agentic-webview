package com.shantoislamdev.agenticwebview

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shantoislamdev.agenticwebview.config.AgenticWebViewConfig
import com.shantoislamdev.agenticwebview.models.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DomEdgeCaseTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val serverRule = MockWebServerRule()

    @Test
    fun testShadowDom() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div id="host"></div>
                <script>
                    const host = document.getElementById('host');
                    const shadow = host.attachShadow({mode: 'open'});
                    shadow.innerHTML = '<button id="shadowBtn">Shadow Button</button>';
                </script>
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
        assertTrue(state.accessibilityTree.contains("Shadow Button"))
    }

    @Test
    fun testOcclusionDetection() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <button id="target" style="position: absolute; left: 0; top: 0; width: 100px; height: 100px;">Target</button>
                <div id="overlay" style="position: absolute; left: 0; top: 0; width: 100px; height: 100px; background: red;">Overlay</div>
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
        val tree = JSONObject(state.accessibilityTree).getJSONArray("tree")
        
        var targetOccluded = false
        for (i in 0 until tree.length()) {
            val node = tree.getJSONObject(i)
            if (node.getString("text") == "Target") {
                targetOccluded = node.getBoolean("occluded")
            }
        }
        
        assertTrue("Target should be occluded", targetOccluded)
    }
}
