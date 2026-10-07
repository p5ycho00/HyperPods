package com.hyperpods.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Module entry point, declared in `assets/xposed_init`.
 *
 * The module only ever injects into the Bluetooth stack (to observe AirPods connections) and
 * SystemUI (read-only probe). Notification readout itself uses the public notification-listener
 * API inside the module app, so no system_server hooking is required.
 */
class HyperPodsEntry : XposedModule() {

    private val bridge by lazy { HookBridge(this) }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        bridge.log("Hook", "模块已注入：${param.processName}（systemServer=${param.isSystemServer}）")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        val packageName = param.packageName
        when {
            packageName in BLUETOOTH_PACKAGES -> {
                runCatching { LifecycleHooks.install(this, param, bridge) }
                    .onFailure { bridge.warn("Hook", "注入心跳初始化失败：${it.message}") }
                bridge.log("Hook", "正在初始化蓝牙监听：$packageName")
                runCatching { BluetoothHooks.install(this, param, bridge) }
                    .onFailure { bridge.error("Hook", "蓝牙监听初始化失败", it) }
            }

            packageName == SYSTEM_UI_PACKAGE -> {
                runCatching { LifecycleHooks.install(this, param, bridge) }
                    .onFailure { bridge.warn("Hook", "注入心跳初始化失败：${it.message}") }
                runCatching { SystemReadoutProbe.install(this, param, bridge) }
                    .onFailure { bridge.warn("Probe", "探测播报类失败：${it.message}") }
            }
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        bridge.log("Hook", "已注入 system_server（本模块未在此安装钩子）")
    }

    private companion object {
        val BLUETOOTH_PACKAGES = setOf(
            "com.android.bluetooth",
            "com.xiaomi.bluetooth",
        )
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
