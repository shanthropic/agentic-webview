package com.shantoislamdev.agenticwebview

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.rules.ExternalResource
import java.util.concurrent.TimeUnit

class MockWebServerRule : ExternalResource() {
    val server = MockWebServer()

    override fun before() {
        server.start()
    }

    override fun after() {
        server.close()
    }

    fun url(path: String): String {
        return server.url(path).toString()
    }

    fun enqueueHtml(html: String) {
        server.enqueue(
            MockResponse.Builder()
                .body(html)
                .addHeader("Content-Type", "text/html")
                .build()
        )
    }

    fun enqueueDelayedHtml(html: String, delayMs: Long) {
        server.enqueue(
            MockResponse.Builder()
                .body(html)
                .addHeader("Content-Type", "text/html")
                .bodyDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
        )
    }

    fun enqueueError(code: Int) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .build()
        )
    }
}
