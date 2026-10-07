package com.hyperpods.hook

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import com.hyperpods.core.A2DP_CONNECTION_STATE_CHANGED
import com.hyperpods.core.ACL_CONNECTED
import com.hyperpods.core.ACL_DISCONNECTED
import com.hyperpods.core.EXTRA_DEVICE
import com.hyperpods.core.EXTRA_PROFILE_PREVIOUS_STATE
import com.hyperpods.core.EXTRA_PROFILE_STATE
import com.hyperpods.core.HEADSET_CONNECTION_STATE_CHANGED
import com.hyperpods.core.PROFILE_A2DP
import com.hyperpods.core.PROFILE_ACL
import com.hyperpods.core.PROFILE_HEADSET
import com.hyperpods.core.STATE_CONNECTED
import com.hyperpods.core.STATE_DISCONNECTED
import com.hyperpods.core.safeAddress
import com.hyperpods.core.safeLabel
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects AirPods connect/disconnect inside the Bluetooth stack process.
 *
 * The Bluetooth stack announces profile state changes with an ordinary broadcast; observing the
 * outgoing intents is far less brittle than hooking the A2DP state machine classes, whose names and
 * shapes differ between AOSP and HyperOS builds. Every version overload of
 * `ContextImpl.sendBroadcast*` is hooked so the permission-guarded variant used by
 * `BluetoothA2dp.sendConnectionStateChange` is covered too.
 */
internal object BluetoothHooks {

    private val lastState = ConcurrentHashMap<String, Boolean>()
    private val reportedNonAirPods = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var sawFirstBroadcast = false

    fun install(module: XposedModule, param: XposedModuleInterface.PackageLoadedParam, bridge: HookBridge) {
        val classLoader = param.defaultClassLoader
        val contextImpl = runCatching {
            Class.forName("android.content.ContextImpl", false, classLoader)
        }.getOrElse {
            bridge.error("Hook", "找不到 ContextImpl，无法监听蓝牙广播", it)
            return
        }

        val methods = runCatching {
            contextImpl.declaredMethods.filter { method ->
                method.name.startsWith("sendBroadcast") &&
                    method.parameterTypes.firstOrNull() == Intent::class.java
            }
        }.getOrDefault(emptyList())

        if (methods.isEmpty()) {
            bridge.error("Hook", "ContextImpl 中没有可用的 sendBroadcast 重载")
            return
        }

        var hooked = 0
        for (method in methods) {
            if (hookOne(module, method, bridge)) hooked++
        }
        bridge.log("Hook", "已在 ${param.packageName} 中挂载 $hooked/${methods.size} 个广播出口")
    }

    private fun hookOne(module: XposedModule, method: Method, bridge: HookBridge): Boolean =
        runCatching {
            module.hook(method)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    runCatching {
                        val intent = chain.getArg(0) as? Intent
                        val context = chain.getThisObject() as? Context
                        if (intent != null && context != null) {
                            onBroadcast(context, intent, bridge)
                        }
                    }.onFailure { bridge.warn("Hook", "处理广播失败：${it.message}") }
                    chain.proceed()
                }
            true
        }.getOrElse {
            bridge.warn("Hook", "挂载 ${method.name} 失败：${it.message}")
            false
        }

    private fun onBroadcast(context: Context, intent: Intent, bridge: HookBridge) {
        // Remember a usable Context so diagnostics can reach the in-app log screen early.
        bridge.rememberContext(context)
        if (!sawFirstBroadcast) {
            sawFirstBroadcast = true
            bridge.log("Hook", "已捕获到蓝牙进程的广播，监听生效")
        }
        when (intent.action) {
            A2DP_CONNECTION_STATE_CHANGED -> handleProfileState(context, intent, bridge, PROFILE_A2DP)
            HEADSET_CONNECTION_STATE_CHANGED -> handleProfileState(context, intent, bridge, PROFILE_HEADSET)
            ACL_CONNECTED -> handleAcl(context, intent, bridge, connected = true)
            ACL_DISCONNECTED -> handleAcl(context, intent, bridge, connected = false)
            else -> return
        }
    }

    private fun handleProfileState(context: Context, intent: Intent, bridge: HookBridge, profile: String) {
        val device = deviceFrom(intent) ?: return
        if (!bridge.isAirPods(device)) {
            val label = device.safeLabel() ?: "?"
            if (reportedNonAirPods.add(label)) {
                bridge.log("Hook", "看到非 AirPods 设备（$profile）：$label")
            }
            return
        }

        val state = intent.getIntExtra(EXTRA_PROFILE_STATE, -1)
        if (state != STATE_CONNECTED && state != STATE_DISCONNECTED) return

        val connected = state == STATE_CONNECTED
        val address = device.safeAddress() ?: return
        if (lastState[address] == connected) return
        lastState[address] = connected

        val previous = intent.getIntExtra(EXTRA_PROFILE_PREVIOUS_STATE, -1)
        bridge.log(
            "Hook",
            "${if (connected) "连接" else "断开"} $profile：${device.safeLabel()}（$previous -> $state）",
        )
        bridge.pushState(context, connected, device, profile, "hook")
    }

    private fun handleAcl(context: Context, intent: Intent, bridge: HookBridge, connected: Boolean) {
        val device = deviceFrom(intent) ?: return
        if (!bridge.isAirPods(device)) return
        // Profile events are authoritative; ACL only reports devices that have no profile event yet.
        val address = device.safeAddress() ?: return
        if (lastState.containsKey(address)) return
        lastState[address] = connected
        bridge.log("Hook", "ACL ${if (connected) "连接" else "断开"}：${device.safeLabel()}")
        bridge.pushState(context, connected, device, PROFILE_ACL, "hook")
    }

    @Suppress("DEPRECATION")
    private fun deviceFrom(intent: Intent): BluetoothDevice? = runCatching {
        intent.getParcelableExtra(EXTRA_DEVICE) as? BluetoothDevice
    }.getOrNull()
}
