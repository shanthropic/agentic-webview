package com.shantoislamdev.agenticwebview

import android.util.Log

object AgenticWebView {
    private const val TAG = "AgenticWebView"

    fun init() {
        Log.d(TAG, "AgenticWebView initialized!")
    }

    fun getVersion(): String {
        return "1.0.0"
    }
}
