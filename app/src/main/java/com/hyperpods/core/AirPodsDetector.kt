package com.hyperpods.core

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import java.util.Locale

/**
 * Decides whether a bonded/connected Bluetooth device is an AirPod.
 *
 * Name keywords are the primary signal (they also catch rebranded clones that identify as
 * "AirPods"), with the Apple OUI prefix as an optional, stricter fallback for renamed devices.
 *
 * This file is loaded inside the hooked Bluetooth process as well, so it deliberately depends on
 * nothing but the framework and the Kotlin standard library.
 */
object AirPodsDetector {

    /**
     * Apple's IEEE OUI prefixes. AirPods always use one of these; kept as a fallback for units the
     * user renamed, and only consulted when explicitly enabled in the settings.
     */
    private val APPLE_OUIS: Set<String> = hashSetOf(
        "00:03:93", "00:0A:27", "00:0A:95", "00:0D:93", "00:10:FA", "00:11:24",
        "00:14:51", "00:16:CB", "00:17:F2", "00:19:E3", "00:1B:63", "00:1C:B3",
        "00:1D:4F", "00:1E:52", "00:1E:C2", "00:1F:5B", "00:1F:F3", "00:21:E9",
        "00:22:41", "00:23:12", "00:23:32", "00:23:6C", "00:23:DF", "00:24:36",
        "00:25:00", "00:25:BC", "00:26:08", "00:26:4A", "00:26:B0", "00:26:BB",
        "00:30:65", "00:3E:E1", "00:50:E4", "00:56:CD", "00:61:71", "00:6D:52",
        "00:88:65", "00:B3:62", "00:C6:10", "00:CD:FE", "00:DB:70", "00:F4:B9",
        "00:F7:6F", "04:0C:CE", "04:15:52", "04:1E:64", "04:26:65", "04:48:9A",
        "04:52:F3", "04:54:53", "04:69:F8", "04:D3:CF", "04:DB:56", "04:E5:36",
        "04:F1:3E", "04:F7:E4", "08:00:07", "08:66:98", "08:6D:41", "08:70:45",
        "08:74:02", "0C:30:21", "0C:3E:9F", "0C:4D:E9", "0C:51:01", "0C:74:C2",
        "0C:77:1A", "0C:BC:9F", "10:1C:0C", "10:40:F3", "10:41:7F", "10:93:E9",
        "10:9A:DD", "10:DD:B1", "14:10:9F", "14:5A:05", "14:7D:DA", "14:8F:C6",
        "14:99:E2", "18:34:51", "18:65:90", "18:81:0E", "18:9E:FC", "18:AF:61",
        "1C:1A:C0", "1C:36:BB", "1C:5C:F2", "1C:9E:46", "1C:AB:A7", "1C:E6:2B",
        "20:78:F0", "20:7D:74", "20:9B:CD", "20:A2:E4", "20:C9:D0", "24:1E:EB",
        "24:A0:74", "24:A2:E1", "24:AB:81", "24:E3:14", "24:F0:94", "24:F6:77",
        "28:0B:5C", "28:37:37", "28:6A:B8", "28:6C:07", "28:CF:DA", "28:CF:E9",
        "28:E0:2C", "28:E7:CF", "2C:1F:23", "2C:20:0B", "2C:B4:3A", "2C:BE:08",
        "2C:F0:A2", "2C:F0:EE", "30:10:E4", "30:35:AD", "30:63:6B", "30:90:AB",
        "30:F7:C5", "34:08:BC", "34:12:98", "34:15:9E", "34:36:3B", "34:51:C9",
        "34:A3:95", "34:AB:37", "34:C0:59", "34:E2:FD", "38:0F:4A", "38:48:4C",
        "38:B5:4D", "38:C9:86", "3C:07:54", "3C:15:C2", "3C:2E:F9", "3C:AB:8E",
        "3C:D0:F8", "3C:E0:72", "40:30:04", "40:33:1A", "40:3C:FC", "40:6C:8F",
        "40:83:DE", "40:98:AD", "40:A6:D9", "40:B3:95", "40:D3:2D", "44:00:10",
        "44:2A:60", "44:4C:0C", "44:D8:84", "44:FB:42", "48:3B:38", "48:43:7C",
        "48:60:BC", "48:74:6E", "48:A1:95", "48:BF:6B", "48:D7:05", "48:E9:F1",
        "4C:32:75", "4C:57:CA", "4C:74:03", "4C:8D:79", "4C:B1:99", "50:32:37",
        "50:82:D5", "50:EA:D6", "54:26:96", "54:4E:90", "54:72:4F", "54:9F:13",
        "54:AE:27", "54:E4:3A", "58:1F:AA", "58:40:4E", "58:55:CA", "58:B0:35",
        "5C:59:48", "5C:95:AE", "5C:96:9D", "5C:97:F3", "5C:F5:DA", "5C:F9:38",
        "60:03:08", "60:33:4B", "60:69:44", "60:8C:4A", "60:92:17", "60:C5:47",
        "60:F4:45", "60:FA:CD", "60:FB:42", "64:20:0C", "64:76:BA", "64:9A:BE",
        "64:B0:A6", "64:B9:E8", "64:E6:82", "68:09:27", "68:5B:35", "68:96:7B",
        "68:9C:70", "68:A8:6D", "68:AB:1E", "68:AE:20", "68:D9:3C", "68:DB:CA",
        "6C:19:C0", "6C:3E:6D", "6C:40:08", "6C:70:9F", "6C:72:E7", "6C:8D:C1",
        "6C:94:66", "6C:AB:31", "6C:C2:6B", "70:11:24", "70:14:A6", "70:3E:AC",
        "70:48:0F", "70:56:81", "70:73:CB", "70:81:EB", "70:A2:B3", "70:CD:60",
        "70:DE:E2", "70:EC:E4", "74:1B:B2", "74:8D:08", "74:E1:B6", "74:E2:F5",
        "78:31:C1", "78:3A:84", "78:4F:43", "78:6C:1C", "78:7B:8A", "78:7E:61",
        "78:88:6D", "78:9F:70", "78:A3:E4", "78:CA:39", "78:D7:5F", "78:FD:94",
        "7C:01:91", "7C:04:D0", "7C:11:BE", "7C:6D:62", "7C:6D:F8", "7C:C3:A1",
        "7C:C5:37", "7C:D1:C3", "7C:F0:5F", "7C:FA:DF", "80:00:6E", "80:49:71",
        "80:92:9F", "80:B0:3D", "80:BE:05", "80:E6:50", "80:EA:96", "84:29:99",
        "84:38:35", "84:78:8B", "84:85:06", "84:89:AD", "84:8E:0C", "84:B1:53",
        "84:FC:FE", "88:19:08", "88:1F:A1", "88:53:95", "88:63:DF", "88:66:A5",
        "88:C6:63", "88:CB:87", "88:E8:7F", "8C:00:6D", "8C:29:37", "8C:2D:AA",
        "8C:58:77", "8C:7B:9D", "8C:7C:92", "8C:85:90", "8C:8E:F2", "8C:FA:BA",
        "90:27:E4", "90:3C:92", "90:60:F1", "90:72:40", "90:84:0D", "90:B0:ED",
        "90:B2:1F", "90:C1:C6", "90:DD:5D", "90:FD:61", "94:94:26", "94:E9:6A",
        "94:F6:A3", "98:01:A7", "98:03:D8", "98:5A:EB", "98:9E:63", "98:B8:E3",
        "98:D6:BB", "98:FE:94", "9C:04:EB", "9C:20:7B", "9C:29:3F", "9C:35:EB",
        "9C:4F:DA", "9C:84:BF", "9C:8B:A0", "9C:E6:5E", "9C:F3:87", "9C:F4:8E",
        "A0:18:28", "A0:3B:E3", "A0:99:9B", "A0:D7:95", "A0:ED:CD", "A4:5E:60",
        "A4:67:06", "A4:83:E7", "A4:B1:97", "A4:C3:61", "A4:D1:8C", "A4:D1:D2",
        "A4:F1:E8", "A8:20:66", "A8:5C:2C", "A8:66:7F", "A8:88:08", "A8:8E:24",
        "A8:96:8A", "A8:BB:CF", "A8:FA:D8", "AC:29:3A", "AC:3C:0B", "AC:61:EA",
        "AC:7F:3E", "AC:87:A3", "AC:BC:32", "AC:CF:5C", "AC:FD:EC", "B0:34:95",
        "B0:65:BD", "B0:9F:BA", "B0:C1:9E", "B0:CA:68", "B4:18:D1", "B4:4B:D2",
        "B4:8B:19", "B4:F0:AB", "B8:09:8A", "B8:17:C2", "B8:41:A4", "B8:44:D9",
        "B8:53:AC", "B8:63:4D", "B8:78:2E", "B8:8D:12", "B8:C1:11", "B8:C7:5D",
        "B8:E8:56", "B8:F6:B1", "B8:FF:61", "BC:3B:AF", "BC:4C:C4", "BC:52:B7",
        "BC:67:78", "BC:6C:21", "BC:92:6B", "BC:9F:EF", "BC:A9:20", "BC:EC:5D",
        "BC:F5:AC", "C0:1A:DA", "C0:63:94", "C0:84:7A", "C0:9F:42", "C0:CE:CD",
        "C0:D0:12", "C0:F2:FB", "C4:2C:03", "C4:B3:01", "C4:BC:4C", "C8:1E:E7",
        "C8:2A:14", "C8:33:4B", "C8:69:CD", "C8:6F:1D", "C8:85:50", "C8:B5:B7",
        "C8:BC:C8", "C8:D0:83", "C8:E0:EB", "CC:08:8D", "CC:20:E8", "CC:25:EF",
        "CC:29:F5", "CC:44:63", "CC:78:5F", "CC:C7:60", "D0:03:4B", "D0:23:DB",
        "D0:25:98", "D0:33:11", "D0:81:7A", "D0:A6:37", "D0:C5:F3", "D0:E1:40",
        "D4:50:3F", "D4:61:9D", "D4:9A:20", "D4:DC:CD", "D4:F4:6F", "D8:00:4D",
        "D8:1D:72", "D8:30:62", "D8:96:95", "D8:9E:3F", "D8:A2:5E", "D8:BB:2C",
        "D8:CF:9C", "D8:D1:CB", "DC:0C:5C", "DC:2B:2A", "DC:2B:61", "DC:37:14",
        "DC:41:5F", "DC:86:D8", "DC:9B:9C", "DC:A4:CA", "DC:A9:04", "DC:D3:21",
        "E0:5F:45", "E0:66:78", "E0:AC:CB", "E0:B5:2D", "E0:B9:BA", "E0:C7:67",
        "E0:C9:7A", "E0:F5:C6", "E0:F8:47", "E4:25:E7", "E4:8B:7F", "E4:9A:79",
        "E4:C6:3D", "E4:CE:8F", "E4:E4:AB", "E8:04:0B", "E8:06:88", "E8:8D:28",
        "E8:B2:AC", "EC:35:86", "EC:85:2F", "EC:AD:B8", "F0:18:98", "F0:24:75",
        "F0:76:6F", "F0:99:BF", "F0:B0:E7", "F0:C1:F1", "F0:CB:A1", "F0:D1:A9",
        "F0:DB:E2", "F0:DB:F8", "F0:DC:E2", "F0:F6:1C", "F4:0F:24", "F4:1B:A1",
        "F4:31:C3", "F4:37:B7", "F4:5C:89", "F4:F1:5A", "F4:F9:51", "F8:1E:DF",
        "F8:27:93", "F8:38:80", "F8:4D:89", "F8:6F:C8", "F8:95:C7", "F8:FF:C2",
        "FC:25:3F", "FC:2A:9C", "FC:3F:DB", "FC:D8:48", "FC:E9:98", "FC:FC:48",
    )

    /** Set of lowercase keywords that identify an AirPod by name. */
    private val FALLBACK_KEYWORDS = DEFAULT_KEYWORDS

    fun isAirPods(
        device: BluetoothDevice?,
        keywords: Set<String> = emptySet(),
        appleFallback: Boolean = false,
    ): Boolean {
        if (device == null) return false
        val names = listOfNotNull(device.safeName(), device.safeAlias())
        if (names.any { matchesKeyword(it, keywords) }) return true
        if (!appleFallback) return false
        val address = device.safeAddress() ?: return false
        return isAppleOui(address) && isAudioDevice(device)
    }

    fun matchesKeyword(name: String?, keywords: Set<String>): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase(Locale.ROOT)
        val candidates = FALLBACK_KEYWORDS + keywords
        return candidates.any { it.isNotBlank() && lower.contains(it.lowercase(Locale.ROOT)) }
    }

    fun isAppleOui(address: String?): Boolean {
        if (address.isNullOrBlank() || address.length < 8) return false
        val prefix = address.substring(0, 8).uppercase(Locale.ROOT)
        return APPLE_OUIS.contains(prefix)
    }

    private fun isAudioDevice(device: BluetoothDevice): Boolean {
        val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
        return major == BluetoothClass.Device.Major.AUDIO_VIDEO || major == null
    }
}

/** [BluetoothDevice.getName] throws without the connect permission on Android 12+. */
fun BluetoothDevice.safeName(): String? = runCatching { name }.getOrNull()

/** [BluetoothDevice.getAlias] is the user-visible, possibly renamed label. */
fun BluetoothDevice.safeAlias(): String? = runCatching { alias }.getOrNull()

fun BluetoothDevice.safeAddress(): String? = runCatching { address }.getOrNull()

/** Best-effort human label: alias first, then the hardware name, then the address. */
fun BluetoothDevice.safeLabel(): String? = safeAlias() ?: safeName() ?: safeAddress()
