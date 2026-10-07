package com.hyperpods.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Best-effort probe for HyperOS' own "notification readout" implementation.
 *
 * Xiaomi gates the built-in readout on its own earbuds, and the class that does the gating is not
 * public. Rather than guessing a hook target and risking SystemUI instability, this probe only
 * reports which candidate classes exist in the running build. The log screen then shows exactly
 * what to target once we see a real HyperOS 4.0 device.
 *
 * Nothing here changes behaviour: it is read-only diagnostics.
 */
internal object SystemReadoutProbe {

    private val CANDIDATES = listOf(
        // HyperOS / MIUI notification readout
        "com.android.systemui.statusbar.notification.voice.NotificationVoiceAnnounce",
        "com.android.systemui.statusbar.notification.NotificationVoiceAnnounce",
        "com.miui.notification.NotificationVoiceAnnounce",
        "com.miui.systemui.notification.NotificationAnnounce",
        "com.android.systemui.bluetooth.BluetoothNotificationAnnounce",
        "com.miui.bluetooth.BluetoothAnnounceHelper",
        "com.android.systemui.statusbar.policy.HeadsetAnnounceController",
        // Xiaomi earbuds integration
        "com.xiaomi.bluetooth.BluetoothEarphoneManager",
        "com.xiaomi.bluetooth.announce.AnnounceManager",
        "miui.bluetooth.BluetoothEarphoneManager",
        // AOSP announcement hooks
        "com.android.systemui.statusbar.notification.collection.coordinator.NotificationVoiceAnnouncementCoordinator",
    )

    fun install(module: XposedModule, param: XposedModuleInterface.PackageLoadedParam, bridge: HookBridge) {
        val classLoader = param.defaultClassLoader
        val found = CANDIDATES.filter { name ->
            runCatching { Class.forName(name, false, classLoader); true }.getOrDefault(false)
        }
        if (found.isEmpty()) {
            bridge.log("Probe", "${param.packageName} 中未发现已知的播报类（将在下次排查时补充）")
        } else {
            bridge.log("Probe", "${param.packageName} 发现候选播报类：${found.joinToString()}")
        }
    }
}
