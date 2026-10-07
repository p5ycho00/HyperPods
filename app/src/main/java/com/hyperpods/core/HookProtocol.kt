package com.hyperpods.core

/*
 * Values in this file are `const`, so Kotlin inlines them into every call site.
 * The hook side (running inside the Bluetooth process) can therefore use them without
 * loading any of the module's UI classes.
 */

/** Package name of this module. */
const val MODULE_PACKAGE: String = "com.hyperpods"

/** Broadcast action the hook uses to talk to the module app. */
const val HOOK_ACTION: String = "com.hyperpods.action.HOOK_EVENT"

const val EXTRA_TYPE: String = "type"
const val EXTRA_TOKEN: String = "token"
const val EXTRA_CONNECTED: String = "connected"
const val EXTRA_DEVICE_NAME: String = "device_name"
const val EXTRA_DEVICE_ADDRESS: String = "device_address"
const val EXTRA_PROFILE: String = "profile"
const val EXTRA_SOURCE: String = "source"
const val EXTRA_LOG_TAG: String = "log_tag"
const val EXTRA_LOG_MESSAGE: String = "log_message"

const val TYPE_STATE: String = "state"
const val TYPE_LOG: String = "log"

/** Remote-preferences group shared between the module app and the hook. */
const val PREFS_GROUP: String = "hyperpods"

const val KEY_ENABLED: String = "enabled"
const val KEY_SPEECH_RATE: String = "speech_rate"
const val KEY_PITCH: String = "pitch"
const val KEY_PACKAGES: String = "enabled_packages"
const val KEY_KEYWORDS: String = "keywords"
const val KEY_APPLE_FALLBACK: String = "apple_oui_fallback"
const val KEY_ANNOUNCE_ONLY_LOCKED: String = "announce_only_when_locked"
const val KEY_ANNOUNCEMENT_MODE: String = "announcement_mode"
const val KEY_GLASS_EFFECTS: String = "glass_effects"
const val KEY_TTS_ENGINE: String = "tts_engine"
const val KEY_THEME_MODE: String = "theme_mode"
const val KEY_TOKEN: String = "token"
const val KEY_SCHEMA: String = "schema"

/** Current preference schema. Bump it when defaults or key meanings change. */
const val PREFS_SCHEMA: Int = 1

/** Default device-name keywords that mark a Bluetooth device as an AirPod. */
val DEFAULT_KEYWORDS: List<String> = listOf("airpods", "airpod", "airpods pro", "airpods max")

/** `BluetoothProfile` state constants, inlined so the hook needs no framework class loading. */
const val STATE_DISCONNECTED: Int = 0
const val STATE_CONNECTED: Int = 2

const val A2DP_CONNECTION_STATE_CHANGED: String =
    "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
const val HEADSET_CONNECTION_STATE_CHANGED: String =
    "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"
const val ACL_CONNECTED: String = "android.bluetooth.device.action.ACL_CONNECTED"
const val ACL_DISCONNECTED: String = "android.bluetooth.device.action.ACL_DISCONNECTED"

const val EXTRA_PROFILE_STATE: String = "android.bluetooth.profile.extra.STATE"
const val EXTRA_PROFILE_PREVIOUS_STATE: String = "android.bluetooth.profile.extra.PREVIOUS_STATE"
const val EXTRA_DEVICE: String = "android.bluetooth.device.extra.DEVICE"

const val PROFILE_A2DP: String = "a2dp"
const val PROFILE_HEADSET: String = "headset"
const val PROFILE_ACL: String = "acl"
