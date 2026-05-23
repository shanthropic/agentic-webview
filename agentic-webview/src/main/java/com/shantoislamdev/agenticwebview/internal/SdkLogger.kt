package com.shantoislamdev.agenticwebview.internal

import android.util.Log

internal class SdkLogger(private val enabled: Boolean) {
    fun d(tag: String, msg: String) {
        if (enabled) Log.d("AgenticSDK:$tag", msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (enabled) Log.e("AgenticSDK:$tag", msg, tr)
    }

    fun i(tag: String, msg: String) {
        if (enabled) Log.i("AgenticSDK:$tag", msg)
    }

    fun w(tag: String, msg: String) {
        if (enabled) Log.w("AgenticSDK:$tag", msg)
    }
}
