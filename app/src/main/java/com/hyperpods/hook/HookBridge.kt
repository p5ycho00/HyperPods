package com.hyperpods.hook

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.hyperpods.core.AirPodsDetector
import com.hyperpods.core.DEFAULT_KEYWORDS
import com.hyperpods.core.EXTRA_CONNECTED
import com.hyperpods.core.EXTRA_DEVICE_ADDRESS
import com.hyperpods.core.EXTRA_DEVICE_NAME
import com.hyperpods.core.EXTRA_LOG_MESSAGE
import com.hyperpods.core.EXTRA_LOG_TAG
import com.hyperpods.core.EXTRA_PROFILE
import com.hyperpods.core.EXTRA_SOURCE
import com.hyperpods.core.EXTRA_TOKEN
import com.hyperpods.core.EXTRA_TYPE
import com.hyperpods.core.HOOK_ACTION
import com.hyperpods.core.KEY_APPLE_FALLBACK
import com.hyperpods.core.KEY_KEYWORDS
import com.hyperpods.core.KEY_TOKEN
import com.hyperpods.core.MODULE_PACKAGE
import com.hyperpods.core.PREFS_GROUP
import com.hyperpods.core.TYPE_LOG
import com.hyperpods.core.TYPE_STATE
import com.hyperpods.core.safeAddress
import com.hyperpods.core.safeLabel
import io.github.libxposed.api.XposedModule

/**
 * Everything the hook needs to talk to the module app and to read the user's configuration.
 *
 * Configuration lives in the framework's remote preferences, which are read-only inside hooked
 * processes. When the framework cannot provide them (PROP_CAP_REMOTE missing) the hook falls back
 * to the built-in defaults so detection still works, just without the user's custom keywords.
 */
internal class HookBridge(private val module: XposedModule) {

    /** Icon-safe logger: also forwards a bounded number of lines to the in-app log screen. */
    private var forwardedLogs = 0
    private var logWindowStart = 0L

    fun log(tag: String, message: String, throwable: Throwable? = null) {
        runCatching { module.log(Log.INFO, TAG, "[$tag] $message", throwable) }
        forwardLog(tag, if (throwable == null) message else "$message: ${throwable.message}")
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        runCatching { module.log(Log.WARN, TAG, "[$tag] $message", throwable) }
        forwardLog(tag, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        runCatching { module.log(Log.ERROR, TAG, "[$tag] $message", throwable) }
        forwardLog(tag, if (throwable == null) message else "$message: ${throwable.message}")
    }

    private fun forwardLog(tag: String, message: String) {
        val now = System.currentTimeMillis()
        if (now - logWindowStart > 60_000L) {
            logWindowStart = now
            forwardedLogs = 0
        }
        if (forwardedLogs >= 40) return
        forwardedLogs++
        // Only usable once the app has been started at least once (it sends its Context along).
        val context = currentContext ?: return
        runCatching {
            context.sendBroadcast(
                Intent(HOOK_ACTION)
                    .setPackage(MODULE_PACKAGE)
                    .putExtra(EXTRA_TYPE, TYPE_LOG)
                    .putExtra(EXTRA_LOG_TAG, tag)
                    .putExtra(EXTRA_LOG_MESSAGE, message)
                    .putExtra(EXTRA_TOKEN, token()),
            )
        }
    }

    private var currentContext: Context? = null

    fun rememberContext(context: Context) {
        currentContext = context
    }

    fun token(): String = prefs()?.getString(KEY_TOKEN, "") ?: ""

    fun keywords(): Set<String> {
        val stored = prefs()?.getStringSet(KEY_KEYWORDS, null)
        return stored?.takeIf { it.isNotEmpty() } ?: DEFAULT_KEYWORDS.toSet()
    }

    fun appleFallback(): Boolean = prefs()?.getBoolean(KEY_APPLE_FALLBACK, false) ?: false

    private var cachedPrefs: SharedPreferences? = null
    private var prefsFailed = false

    private fun prefs(): SharedPreferences? {
        cachedPrefs?.let { return it }
        if (prefsFailed) return null
        return runCatching { module.getRemotePreferences(PREFS_GROUP) }
            .onFailure {
                prefsFailed = true
                runCatching {
                    module.log(Log.WARN, TAG, "[Prefs] 无法读取远程偏好，使用默认配置", it)
                }
            }
            .getOrNull()
            ?.also { cachedPrefs = it }
    }

    fun isAirPods(device: BluetoothDevice?): Boolean = AirPodsDetector.isAirPods(
        device = device,
        keywords = keywords(),
        appleFallback = appleFallback(),
    )

    /** Pushes a connection state change to the module app. */
    fun pushState(
        context: Context,
        connected: Boolean,
        device: BluetoothDevice?,
        profile: String,
        source: String,
    ) {
        rememberContext(context)
        runCatching {
            context.sendBroadcast(
                Intent(HOOK_ACTION)
                    .setPackage(MODULE_PACKAGE)
                    .putExtra(EXTRA_TYPE, TYPE_STATE)
                    .putExtra(EXTRA_CONNECTED, connected)
                    .putExtra(EXTRA_DEVICE_NAME, device?.safeLabel())
                    .putExtra(EXTRA_DEVICE_ADDRESS, device?.safeAddress())
                    .putExtra(EXTRA_PROFILE, profile)
                    .putExtra(EXTRA_SOURCE, source)
                    .putExtra(EXTRA_TOKEN, token()),
            )
        }.onFailure { log("Hook", "推送连接状态失败：${it.message}") }
    }

    companion object {
        const val TAG = "HyperPods"
    }
}
