package com.hyperpods.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HeadsetState(
    val connected: Boolean = false,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val profile: String? = null,
    val source: String? = null,
    val updatedAt: Long = 0L,
)

/** Live AirPods connection state, fed by the hook first and the in-app Bluetooth fallback second. */
object ConnectionState {
    private val _state = MutableStateFlow(HeadsetState())
    val state: StateFlow<HeadsetState> = _state.asStateFlow()

    fun update(
        connected: Boolean,
        deviceName: String?,
        deviceAddress: String?,
        profile: String?,
        source: String,
    ) {
        val previous = _state.value
        _state.value = HeadsetState(
            connected = connected,
            deviceName = deviceName ?: previous.deviceName,
            deviceAddress = deviceAddress ?: previous.deviceAddress,
            profile = profile ?: previous.profile,
            source = source,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun clearDevice() {
        _state.value = HeadsetState()
    }
}
