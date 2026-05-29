package dev.shantoislam.agenticwebview

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.shantoislam.agenticwebview.config.AgenticWebViewConfig
import dev.shantoislam.agenticwebview.models.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoordinateMappingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val serverRule = MockWebServerRule()

    @Test
    fun testCoordinateAccuracy() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html>
            <body style="margin: 0; padding: 0;">
                <button id="btn" style="position: absolute; left: 100px; top: 200px; width: 50px; height: 30px;" onclick="this.innerText='Clicked'">Click</button>
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
        val btn = JSONObject(state.accessibilityTree).getJSONArray("tree").getJSONObject(0)
        
        assertEquals("100", btn.getJSONObject("bounds").getString("left"))
        assertEquals("200", btn.getJSONObject("bounds").getString("top"))

        // Click it
        controller.executeAction(AgentAction.Click("1"))
        
        Thread.sleep(500)
        val newState = (controller.captureState() as AgentResult.Success).data
        assertTrue(newState.accessibilityTree.contains("Clicked"))
    }
}
