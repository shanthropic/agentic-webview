package com.shantoislamdev.webviewagent.library

import android.util.Log

object WebviewAgent {
    private const val TAG = "WebviewAgent"

    fun init() {
        Log.d(TAG, "WebviewAgent initialized!")
    }

    fun getVersion(): String {
        return "1.0.0"
    }
}
