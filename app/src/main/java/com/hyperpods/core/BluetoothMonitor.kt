package com.hyperpods.core

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.hyperpods.readout.ReadoutController

/**
 * Fallback AirPods detection that runs inside the module's own process using the public
 * Bluetooth API. The hook is the primary path; this keeps the feature working when the module is
 * not active yet (or on a build where the Bluetooth process rejects our hook).
 */
object BluetoothMonitor {

    private var registered = false
    private var lastAdapterReport: String? = null
    private var lastAudioReport: String? = null
    private var warnedMissingPermission = false

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun start(context: Context) {
        if (registered) return
        registered = true
        val filter = IntentFilter().apply {
            addAction(A2DP_CONNECTION_STATE_CHANGED)
            addAction(HEADSET_CONNECTION_STATE_CHANGED)
            addAction(ACL_CONNECTED)
            addAction(ACL_DISCONNECTED)
        }
        runCatching {
            val appContext = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(receiver, filter)
            }
        }.onFailure { EventLog.warn("蓝牙", "注册蓝牙监听失败：${it.message}") }
        refresh(context)
    }

    fun refresh(context: Context) {
        // Works without any runtime permission and names the device, so it is tried first.
        if (detectViaAudioOutput(context)) return

        // No AirPods among the active audio outputs: treat it as disconnected, unless the hook
        // (which sees the real profile events) has the final word.
        if (ConnectionState.state.value.source != "hook") {
            ConnectionState.update(false, null, null, null, "audio")
        }

        if (!hasPermission(context)) {
            // refresh() runs on every Bluetooth broadcast; warn once instead of on every event.
            if (!warnedMissingPermission) {
                warnedMissingPermission = true
                EventLog.warn("蓝牙", "缺少「附近的设备」权限，无法自行检测连接状态")
            }
            return
        }
        val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()
        if (adapter == null || !adapter.isEnabled) {
            ConnectionState.update(false, null, null, null, "app")
            return
        }
        reportAdapter(context, adapter)
        val appContext = context.applicationContext
        runCatching {
            adapter.getProfileProxy(appContext, profileListener(appContext), BluetoothProfile.A2DP)
        }.onFailure { EventLog.warn("蓝牙", "读取 A2DP 连接状态失败：${it.message}") }
    }

    /**
     * Inspects the active audio outputs. [AudioDeviceInfo.getProductName] carries the Bluetooth
     * device name, and unlike the Bluetooth APIs this needs no permission at all — so a user who
     * does not want to grant "nearby devices" still gets a working fallback.
     */
    /**
     * Synchronous check of the active audio outputs: true when an AirPods-like device is the
     * current wireless output. Unlike the profile-proxy path this needs no callback round trip, so
     * it is safe to call while deciding whether a notification should be read.
     */
    fun checkAirPodsAudioOutput(context: Context): Boolean = detectViaAudioOutput(context)

    /**
     * Whether speech would actually come out of the AirPods right now: either the active audio
     * output already routes there, or the hook — which sees the real profile events — says the
     * headset is connected. Announcements and the preview button share this one gate so they can
     * never disagree about whether it is safe to speak.
     */
    fun isRoutedToAirPods(context: Context): Boolean {
        if (checkAirPodsAudioOutput(context)) return true
        val connection = ConnectionState.state.value
        return connection.connected && connection.source == "hook"
    }

    private fun detectViaAudioOutput(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val outputs = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }.getOrElse {
            EventLog.warn("音频", "读取音频输出失败：${it.message}")
            return false
        }
        val wireless = outputs.filter {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
        if (wireless.isEmpty()) return false

        val settings = SettingsStore.state.value
        val airPods = wireless.firstOrNull { device ->
            AirPodsDetector.matchesKeyword(
                device.productName?.toString(),
                settings.keywords,
            )
        }
        val names = wireless.map { it.productName?.toString() ?: "?" }
        if (airPods != null) {
            val wasConnected = ConnectionState.state.value.connected
            ConnectionState.update(
                connected = true,
                deviceName = airPods.productName?.toString(),
                deviceAddress = null,
                profile = PROFILE_A2DP,
                source = "audio",
            )
            if (!wasConnected) {
                ReadoutController.onConnectionChanged(context, true, airPods.productName?.toString())
            }
            return true
        }
        // A non-AirPods headset is connected: report the names so the allow-list can be tuned.
        if (names.toString() != lastAudioReport) {
            lastAudioReport = names.toString()
            EventLog.info("音频", "当前无线输出：${names.joinToString()}（不是 AirPods）")
        }
        return false
    }

    /**
     * Records what the system reports, so "AirPods 未连接" can be told apart from "the app cannot
     * see Bluetooth at all". Only logged when the picture changes.
     */
    private fun reportAdapter(context: Context, adapter: BluetoothAdapter) {
        val report = runCatching {
            val a2dp = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
            val bonded = adapter.bondedDevices.orEmpty().map { it.safeLabel() ?: "?" }
            "A2DP状态=$a2dp，已配对=${bonded.joinToString()}"
        }.getOrElse { "读取失败：${it.message}" }
        if (report != lastAdapterReport) {
            lastAdapterReport = report
            EventLog.info("蓝牙", report)
        }
    }

    /** Built per call so the listener can reach a Context for logging and announcements. */
    private fun profileListener(context: Context) = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            runCatching {
                val connected = proxy.connectedDevices
                val airPods = connected.firstOrNull { device ->
                    AirPodsDetector.isAirPods(
                        device = device,
                        keywords = SettingsStore.state.value.keywords,
                        appleFallback = SettingsStore.state.value.appleOuiFallback,
                    )
                }
                if (airPods != null) {
                    val wasConnected = ConnectionState.state.value.connected
                    ConnectionState.update(
                        connected = true,
                        deviceName = airPods.safeName(),
                        deviceAddress = airPods.safeAddress(),
                        profile = PROFILE_A2DP,
                        source = "app",
                    )
                    if (!wasConnected) {
                        ReadoutController.onConnectionChanged(context, true, airPods.safeLabel())
                    }
                } else if (ConnectionState.state.value.source != "hook") {
                    ConnectionState.update(false, null, null, null, "app")
                }
            }.onFailure { EventLog.warn("蓝牙", "解析已连接设备失败：${it.message}") }
            runCatching {
                BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.A2DP, proxy)
            }
        }

        override fun onServiceDisconnected(profile: Int) = Unit
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val device = deviceExtra(intent)
            val profile = when (action) {
                A2DP_CONNECTION_STATE_CHANGED -> PROFILE_A2DP
                HEADSET_CONNECTION_STATE_CHANGED -> PROFILE_HEADSET
                else -> PROFILE_ACL
            }
            if (action == ACL_DISCONNECTED) {
                if (ConnectionState.state.value.source != "hook") {
                    ConnectionState.update(false, null, null, profile, "app")
                    ReadoutController.onConnectionChanged(context, false, null)
                }
                return
            }
            if (action == A2DP_CONNECTION_STATE_CHANGED || action == HEADSET_CONNECTION_STATE_CHANGED) {
                val state = intent.getIntExtra(EXTRA_PROFILE_STATE, -1)
                if (state == STATE_CONNECTED && isOurDevice(context, device)) {
                    val wasConnected = ConnectionState.state.value.connected
                    ConnectionState.update(
                        connected = true,
                        deviceName = device?.safeName(),
                        deviceAddress = device?.safeAddress(),
                        profile = profile,
                        source = "app",
                    )
                    if (!wasConnected) {
                        ReadoutController.onConnectionChanged(context, true, device?.safeLabel())
                    }
                } else if (state == STATE_DISCONNECTED && device?.let { isOurDevice(context, it) } == true) {
                    if (ConnectionState.state.value.source != "hook") {
                        ConnectionState.update(false, null, null, profile, "app")
                        ReadoutController.onConnectionChanged(context, false, null)
                    }
                }
            }
        }
    }

    private fun isOurDevice(context: Context, device: BluetoothDevice?): Boolean {
        if (device == null) return false
        // Without the connect permission the device name is unavailable, so we must not claim the
        // device is an AirPods. This used to return true "to be permissive", which made every
        // Bluetooth device — a car, a speaker — open the readout gate.
        if (!hasPermission(context)) return false
        return AirPodsDetector.isAirPods(
            device = device,
            keywords = SettingsStore.state.value.keywords,
            appleFallback = SettingsStore.state.value.appleOuiFallback,
        )
    }

    @Suppress("DEPRECATION")
    private fun deviceExtra(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_DEVICE)
        }
}
