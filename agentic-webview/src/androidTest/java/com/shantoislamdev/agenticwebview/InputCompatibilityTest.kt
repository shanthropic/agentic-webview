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
class InputCompatibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val serverRule = MockWebServerRule()

    @Test
    fun testFrameworkSafeInput() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <input type="text" id="input" oninput="this.setAttribute('data-val', this.value)">
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

        val result = controller.executeAction(AgentAction.InputText("1", "Hello SDK"))
        assertTrue(result is AgentResult.Success)

        Thread.sleep(500)
        val state = (controller.captureState() as AgentResult.Success).data
        assertTrue(state.accessibilityTree.contains("Hello SDK"))
    }
}
