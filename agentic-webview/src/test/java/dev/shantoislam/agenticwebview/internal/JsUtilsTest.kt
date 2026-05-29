package dev.shantoislam.agenticwebview.internal

import dev.shantoislam.agenticwebview.internal.JsUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class JsUtilsTest {

    @Test
    fun testEscapeJs() {
        val input = "Hello 'World' \"Quotes\" \\ Backslash \n Newline"
        val expected = "Hello \\'World\\' \\\"Quotes\\\" \\\\ Backslash \\n Newline"
        assertEquals(expected, JsUtils.escapeJs(input))
    }

    @Test
    fun testEscapeJsInjection() {
        val input = "'); alert('XSS'); //"
        val expected = "\\'); alert(\\'XSS\\'); //"
        assertEquals(expected, JsUtils.escapeJs(input))
    }
}
