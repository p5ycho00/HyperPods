package com.hyperpods.readout

import android.content.Context
import com.hyperpods.core.BluetoothMonitor
import com.hyperpods.core.SettingsStore

/**
 * Entry point shared by the hook broadcast receiver and the in-app Bluetooth fallback: keeps the
 * headset state fresh and drops queued speech as soon as the headset goes away.
 */
object ReadoutController {

    fun onStart(context: Context) {
        SettingsStore.init(context)
        TtsSpeaker.ensure(context)
        BluetoothMonitor.start(context)
    }

    fun onConnectionChanged(context: Context, connected: Boolean, deviceName: String?) {
        SettingsStore.init(context)
        if (!connected) {
            // Speaking only happens while the headset is connected, so nothing may stay queued.
            Announcer.stop()
        }
    }
}
