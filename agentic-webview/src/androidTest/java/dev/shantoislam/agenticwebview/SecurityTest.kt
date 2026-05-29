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
class SecurityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val serverRule = MockWebServerRule()

    @Test
    fun testBlockedHost() = runBlocking {
        serverRule.enqueueHtml("<html><body>Host</body></html>")

        val config = AgenticWebViewConfig(allowedHosts = setOf("trusted.com"))
        val controller = AgenticWebController(config)

        composeTestRule.setContent {
            AgenticWebViewComposable(controller = controller, config = config)
        }

        // Navigate to a non-trusted host (localhost from MockWebServer)
        controller.executeAction(AgentAction.Navigate(serverRule.url("/")))
        
        // The URL should not be loaded, or at least the state should reflect failure if we had better tracking.
        // For now we check if it's still IDLE or LOADING if it was blocked.
        // Actually, shouldOverrideUrlLoading blocks it.
        
        val state = (controller.captureState() as AgentResult.Success).data
        assertNotEquals(serverRule.url("/"), state.url)
    }
}
