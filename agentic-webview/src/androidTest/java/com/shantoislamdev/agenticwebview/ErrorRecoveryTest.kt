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
class ErrorRecoveryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val serverRule = MockWebServerRule()

    @Test
    fun testNavigationTimeout() = runBlocking {
        // Enqueue a response that never finishes or takes too long
        serverRule.enqueueDelayedHtml("<html><body>Delayed</body></html>", 5000)

        val config = AgenticWebViewConfig(pageSettleTimeoutMs = 1000)
        val controller = AgenticWebController(config)

        composeTestRule.setContent {
            AgenticWebViewComposable(controller = controller, config = config)
        }

        val result = controller.executeAction(AgentAction.Navigate(serverRule.url("/")))
        assertTrue(result is AgentResult.Error)
        assertTrue((result as AgentResult.Error).error is AgentError.Timeout)
    }

    @Test
    fun testElementNotFound() = runBlocking {
        serverRule.enqueueHtml("<html><body>Empty</body></html>")

        val config = AgenticWebViewConfig()
        val controller = AgenticWebController(config)

        composeTestRule.setContent {
            AgenticWebViewComposable(controller = controller, config = config)
        }

        controller.executeAction(AgentAction.Navigate(serverRule.url("/")))

        val result = controller.executeAction(AgentAction.Click("non-existent"))
        assertTrue(result is AgentResult.Error)
        assertTrue((result as AgentResult.Error).error is AgentError.ElementNotFound)
    }
}
