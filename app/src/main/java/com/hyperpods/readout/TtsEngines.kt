package com.hyperpods.readout

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import com.hyperpods.core.EventLog

/** One selectable speech engine; a null [packageName] means "whatever the system defaults to". */
data class TtsEngineOption(
    val packageName: String?,
    val label: String,
)

object TtsEngines {

    fun installed(context: Context): List<TtsEngineOption> {
        val options = mutableListOf(TtsEngineOption(null, "系统默认"))
        val seen = mutableSetOf<String>()
        runCatching {
            val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            context.packageManager.queryIntentServices(intent, 0).forEach { info ->
                val packageName = info.serviceInfo?.packageName ?: return@forEach
                if (!seen.add(packageName)) return@forEach
                val label = runCatching {
                    val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                    context.packageManager.getApplicationLabel(appInfo).toString()
                }.getOrDefault(packageName)
                options += TtsEngineOption(packageName, label)
            }
        }.onFailure { EventLog.warn("播报", "读取语音引擎列表失败：${it.message}") }
        return options
    }

    /** True when the device has no alternative engine, i.e. rate/pitch cannot be worked around. */
    fun hasAlternative(context: Context): Boolean = installed(context).size > 1

    fun describe(context: Context): String =
        installed(context).joinToString { option ->
            option.packageName?.let { "$it" } ?: "系统默认"
        }

    fun matches(option: TtsEngineOption, packageName: String?): Boolean =
        option.packageName.equals(packageName, ignoreCase = true)
}
