package dev.shantoislam.agenticwebview

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.shantoislam.agenticwebview.config.AgenticWebViewConfig
import dev.shantoislam.agenticwebview.models.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ConcurrencyStressTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val serverRule = MockWebServerRule()

    @Test
    fun testConcurrentCaptureState() = runBlocking {
        serverRule.enqueueHtml("<html><body><button id='btn'>Btn</button></body></html>")
        
        val config = AgenticWebViewConfig(enableDebugLogging = true)
        val controller = AgenticWebController(config)

        composeTestRule.setContent {
            AgenticWebViewComposable(controller = controller, config = config)
        }

        controller.executeAction(AgentAction.Navigate(serverRule.url("/")))

        val successCount = AtomicInteger(0)
        val jobs = List(50) {
            launch(Dispatchers.Default) {
                val result = controller.captureState()
                if (result is AgentResult.Success) {
                    successCount.incrementAndGet()
                }
            }
        }

        jobs.joinAll()
        assertEquals(50, successCount.get())
    }
}
