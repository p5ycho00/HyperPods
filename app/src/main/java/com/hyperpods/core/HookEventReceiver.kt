package com.hyperpods.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.hyperpods.readout.ReadoutController

/** AID_BLUETOOTH; not exposed as a public constant by the SDK. */
private const val BLUETOOTH_UID = 1002

/**
 * Receives the hook's pushes: connection state changes and log lines.
 *
 * The receiver is exported because system processes cannot target a non-exported component.
 * Senders are therefore validated: on Android 14+ we can read the sending uid directly, and the
 * hook additionally echoes a per-install token it reads from the shared preferences.
 */
class HookEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HOOK_ACTION) return
        SettingsStore.init(context)
        if (!isTrusted(intent, sentFromUid)) {
            EventLog.warn("Hook", "已忽略来源不可信的广播")
            return
        }

        when (intent.getStringExtra(EXTRA_TYPE)) {
            TYPE_STATE -> handleState(context, intent)
            TYPE_LOG -> handleLog(intent)
        }
    }

    private fun isTrusted(intent: Intent, senderUid: Int): Boolean {
        val expected = SettingsStore.token.value
        val provided = intent.getStringExtra(EXTRA_TOKEN)
        if (expected.isNotEmpty() && provided == expected) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return when (senderUid) {
            Process.SYSTEM_UID, BLUETOOTH_UID, Process.myUid(), 0 -> true
            else -> false
        }
    }

    private fun handleState(context: Context, intent: Intent) {
        val connected = intent.getBooleanExtra(EXTRA_CONNECTED, false)
        val name = intent.getStringExtra(EXTRA_DEVICE_NAME)
        val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        val profile = intent.getStringExtra(EXTRA_PROFILE)
        ConnectionState.update(
            connected = connected,
            deviceName = name,
            deviceAddress = address,
            profile = profile,
            source = "hook",
        )
        EventLog.info(
            "Hook",
            if (connected) "AirPods 已连接：${name ?: address ?: "未知设备"}（$profile）"
            else "AirPods 已断开",
        )
        ReadoutController.onStart(context)
        ReadoutController.onConnectionChanged(context, connected, name)
    }

    private fun handleLog(intent: Intent) {
        val tag = intent.getStringExtra(EXTRA_LOG_TAG) ?: "Hook"
        val message = intent.getStringExtra(EXTRA_LOG_MESSAGE) ?: return
        EventLog.info(tag, message)
    }
}
