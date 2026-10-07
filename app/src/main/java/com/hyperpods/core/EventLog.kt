package com.hyperpods.core

import android.util.Log

/**
 * The module's one logging entry point.
 *
 * There is no in-app log any more — nothing is kept in memory or written to a file. Lines still go
 * to logcat (`adb logcat -s HyperPods`), which costs nothing while keeping the module debuggable.
 */
object EventLog {
    private const val TAG = "HyperPods"

    fun info(tag: String, message: String) {
        Log.i(TAG, "[$tag] $message")
    }

    fun warn(tag: String, message: String) {
        Log.w(TAG, "[$tag] $message")
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$tag] $message", throwable)
    }
}
