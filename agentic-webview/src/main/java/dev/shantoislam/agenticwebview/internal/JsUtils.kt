package dev.shantoislam.agenticwebview.internal

object JsUtils {
    /**
     * Escapes a string for use in a JavaScript string literal.
     */
    fun escapeJs(input: String?): String {
        if (input == null) return "null"
        return input.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
