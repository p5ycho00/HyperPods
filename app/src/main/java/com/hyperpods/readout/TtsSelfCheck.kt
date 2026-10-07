package com.hyperpods.readout

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.hyperpods.core.EventLog
import com.hyperpods.core.SettingsStore
import java.io.File
import java.util.Locale

/**
 * Answers "why doesn't changing the speed do anything" with a measurement instead of a guess.
 *
 * The same sentence is synthesised twice into cache files — once slowly, once quickly — and the
 * two produced durations are compared. If they are the same, the engine ignores the standard rate
 * parameter and no amount of calling `setSpeechRate` will help; if they differ, the engine honours
 * it and a missing change must come from the call path instead.
 */
object TtsSelfCheck {

    private const val TAG = "自检"
    private const val SLOW_ID = "hyperpods-probe-slow"
    private const val FAST_ID = "hyperpods-probe-fast"
    private const val TEXT = "通知播报自检，一二三四五六七八九十"

    fun run(context: Context) {
        val appContext = context.applicationContext
        EventLog.info(TAG, "当前引擎：${TtsSpeaker.currentEngineName()}，语言：${TtsSpeaker.currentVoice()}")
        val installed = TtsEngines.installed(appContext)
        EventLog.info(TAG, "已安装引擎：${installed.joinToString { "${it.label}(${it.packageName ?: "default"})" }}")
        val selected = SettingsStore.state.value.ttsEngine.takeIf { it.isNotBlank() }
        EventLog.info(TAG, "本次自检使用：${selected ?: "系统默认"}")

        val slowFile = File(appContext.cacheDir, "tts-probe-slow.wav")
        val fastFile = File(appContext.cacheDir, "tts-probe-fast.wav")
        listOf(slowFile, fastFile).forEach { runCatching { it.delete() } }

        var probe: TextToSpeech? = null
        var finished = false

        fun finish(message: String) {
            if (finished) return
            finished = true
            EventLog.info(TAG, message)
            runCatching { probe?.shutdown() }
        }

        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                when (utteranceId) {
                    SLOW_ID -> probe?.let { engine ->
                        // setSpeechRate is the same public call the readout path uses.
                        engine.setSpeechRate(FAST_RATE)
                        engine.synthesizeToFile(TEXT, null, fastFile, FAST_ID)
                    }

                    FAST_ID -> {
                        val slow = durationOf(slowFile)
                        val fast = durationOf(fastFile)
                        finish(describe(slow, fast))
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish("合成失败，无法完成自检")

            override fun onError(utteranceId: String?, errorCode: Int) =
                finish("合成失败（errorCode=$errorCode）")
        }

        probe = runCatching {
            val listenerInit = TextToSpeech.OnInitListener { status ->
                if (status != TextToSpeech.SUCCESS) {
                    finish("第二个引擎实例初始化失败（status=$status）")
                } else {
                    val engine = probe
                    if (engine != null) {
                        engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                        engine.setOnUtteranceProgressListener(listener)
                        engine.setSpeechRate(SLOW_RATE)
                        engine.synthesizeToFile(TEXT, null, slowFile, SLOW_ID)
                    }
                }
            }
            if (selected != null) {
                TextToSpeech(appContext, listenerInit, selected)
            } else {
                TextToSpeech(appContext, listenerInit)
            }
        }.getOrElse {
            finish("无法创建自检引擎：${it.message}")
            return
        }
    }

    private fun durationOf(file: File): Long {
        if (!file.exists() || file.length() == 0L) return -1L
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: -1L
        }.getOrDefault(-1L).also { runCatching { retriever.release() } }
    }

    private fun describe(slowMs: Long, fastMs: Long): String {
        if (slowMs <= 0 || fastMs <= 0) {
            return "自检失败：无法读取合成文件的时长（slow=$slowMs, fast=$fastMs）"
        }
        val ratio = slowMs.toDouble() / fastMs.toDouble()
        return if (ratio > 1.3) {
            "语速有效：%.2fx 时长 %d ms，%.2fx 时长 %d ms（相差 %.1f 倍）——引擎支持调节".format(
                SLOW_RATE, slowMs, FAST_RATE, fastMs, ratio,
            )
        } else {
            "语速无效：%.2fx 时长 %d ms，%.2fx 时长 %d ms（几乎相同）——该引擎忽略语速设置，请更换语音引擎".format(
                SLOW_RATE, slowMs, FAST_RATE, fastMs,
            )
        }
    }

    private const val SLOW_RATE = 0.5f
    private const val FAST_RATE = 2.0f
}
