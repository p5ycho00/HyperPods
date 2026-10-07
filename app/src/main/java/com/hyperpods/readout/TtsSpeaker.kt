package com.hyperpods.readout

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.hyperpods.core.EventLog
import com.hyperpods.core.BluetoothMonitor
import com.hyperpods.core.SettingsStore
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns the TTS engine.
 *
 * Two details matter on HyperOS:
 *  - `USAGE_MEDIA` is the usage that actually routes to the connected A2DP device; the TTS default
 *    (`USAGE_ASSISTANT`) can come out of the phone speaker instead.
 *  - Xiaomi ships its own engine (`com.xiaomi.mibrain.speech`) which honours rate and pitch, so it
 *    is preferred when installed, with an automatic fallback to whatever the system default is.
 */
object TtsSpeaker {

    const val XIAOMI_TTS_PACKAGE = "com.xiaomi.mibrain.speech"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val utteranceCounter = AtomicInteger(0)

    private var engine: TextToSpeech? = null
    private var ready = false
    private var initializing = false
    private var generation = 0
    private var pendingText: String? = null
    private var pendingUtteranceId: String? = null
    private var focusRequest: AudioFocusRequest? = null

    @Volatile
    private var appContext: Context? = null

    /** Invoked on the main thread when the current utterance finishes (or fails). */
    @Volatile
    private var onUtteranceFinished: (() -> Unit)? = null

    /**
     * Accessibility usage on purpose. Media usage makes MIUI pop the volume panel every time an
     * announcement starts, and this is the usage the reference AirPods implementations use for
     * spoken notifications — it still routes to the connected headset and still ducks music.
     */
    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    val isReady: Boolean get() = ready

    fun currentEngineName(): String =
        runCatching { engine?.defaultEngine }.getOrNull() ?: "未初始化"

    fun currentVoice(): String =
        runCatching { engine?.voice?.locale?.toLanguageTag() }.getOrNull() ?: "未知"

    fun ensure(context: Context) {
        if (engine != null || initializing) return
        SettingsStore.init(context)
        appContext = context.applicationContext
        initializing = true
        initialize(context.applicationContext, preferredEngine(context))
    }

    private fun initialize(appContext: Context, enginePackage: String?) {
        val localGeneration = ++generation
        val listener = TextToSpeech.OnInitListener { status ->
            onInitialized(appContext, status, localGeneration, enginePackage)
        }
        val created = runCatching {
            if (enginePackage != null) {
                TextToSpeech(appContext, listener, enginePackage)
            } else {
                TextToSpeech(appContext, listener)
            }
        }.getOrElse { throwable ->
            EventLog.error("播报", "创建语音引擎失败：${throwable.message}")
            initializing = false
            return
        }
        engine = created
    }

    private fun onInitialized(
        appContext: Context,
        status: Int,
        localGeneration: Int,
        enginePackage: String?,
    ) {
        if (localGeneration != generation) return
        if (status != TextToSpeech.SUCCESS) {
            // The Xiaomi engine can be present but unusable; fall back to the system default once.
            if (enginePackage != null) {
                EventLog.warn("播报", "小爱语音引擎初始化失败，改用系统默认引擎")
                runCatching { engine?.shutdown() }
                engine = null
                initializing = false
                ensureFallback(appContext)
                return
            }
            EventLog.error("播报", "语音引擎初始化失败（status=$status）")
            initializing = false
            return
        }
        initializing = false
        val tts = engine ?: return
        runCatching {
            tts.setAudioAttributes(audioAttributes)
            val languageResult = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                EventLog.warn("播报", "系统缺少中文语音数据，将使用默认语音")
            }
            tts.setOnUtteranceProgressListener(progressListener)
        }.onFailure { EventLog.warn("播报", "配置语音引擎失败：${it.message}") }
        ready = true
        applyTuning()
        EventLog.info("播报", "语音引擎就绪：${runCatching { tts.defaultEngine }.getOrNull() ?: "?"}")

        val text = pendingText
        val id = pendingUtteranceId
        pendingText = null
        pendingUtteranceId = null
        if (text != null && id != null) {
            speakNow(appContext, text, id)
        }
    }

    private fun ensureFallback(appContext: Context) {
        if (engine != null || initializing) return
        initializing = true
        generation++
        val localGeneration = generation
        engine = TextToSpeech(appContext) { status ->
            onInitialized(appContext, status, localGeneration, null)
        }
    }

    private fun preferredEngine(context: Context): String? {
        // The user picks the engine in settings; rate/pitch support differs wildly between them.
        return SettingsStore.state.value.ttsEngine.takeIf { it.isNotBlank() }
    }

    /** Tears the engine down and builds it again, e.g. after the user switches engines. */
    fun reinitialize(context: Context) {
        shutdown()
        ensure(context)
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            // Nothing to log: the spoken text was already recorded when the utterance was queued.
        }

        override fun onDone(utteranceId: String?) {
            mainHandler.post { finishUtterance() }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            EventLog.warn("播报", "语音合成失败（旧接口回调）")
            mainHandler.post { finishUtterance() }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            EventLog.warn("播报", "语音合成失败：errorCode=$errorCode")
            mainHandler.post { finishUtterance() }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            mainHandler.post { finishUtterance() }
        }
    }

    private fun finishUtterance() {
        abandonFocus()
        val callback = onUtteranceFinished
        onUtteranceFinished = null
        callback?.invoke()
    }

    fun applyTuning() {
        val settings = SettingsStore.state.value
        setTuning(settings.speechRate, settings.pitch)
    }

    /**
     * Applies rate and pitch straight to the engine, so the next utterance already uses them
     * without waiting for a preference write to land.
     */
    fun setTuning(rate: Float, pitch: Float) {
        engine?.let { tts ->
            runCatching {
                tts.setSpeechRate(rate.coerceIn(0.3f, 2.5f))
                tts.setPitch(pitch.coerceIn(0.3f, 2.0f))
            }.onFailure { EventLog.warn("播报", "设置语速/音调失败：${it.message}") }
        }
    }

    /**
     * Speaks [text]. [onDone] runs once the utterance finishes, which is what lets the caller keep
     * a queue without talking over itself.
     */
    fun speak(context: Context, text: String, onDone: (() -> Unit)? = null): Boolean {
        if (text.isBlank()) return false
        ensure(context)
        val utteranceId = "hyperpods-${utteranceCounter.incrementAndGet()}"
        if (!ready) {
            pendingText = text
            pendingUtteranceId = utteranceId
            onUtteranceFinished = onDone
            return false
        }
        onUtteranceFinished = onDone
        return speakNow(context, text, utteranceId)
    }

    private fun speakNow(context: Context, text: String, utteranceId: String): Boolean {
        val tts = engine ?: return false
        applyTuning()
        requestDuckingFocus(context)
        val parameters = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            // Xiaomi's engine plays on the music stream whatever the audio attributes say, and
            // MIUI shows the volume panel whenever that stream starts while idle. Asking for the
            // accessibility stream explicitly keeps the panel away; it is grouped with the media
            // volume, so the level and the routing are unchanged.
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ACCESSIBILITY)
        }
        val result = runCatching {
            tts.speak(text, TextToSpeech.QUEUE_ADD, parameters, utteranceId)
        }.getOrElse {
            EventLog.warn("播报", "朗读失败：${it.message}")
            TextToSpeech.ERROR
        }
        if (result != TextToSpeech.SUCCESS) {
            EventLog.warn("播报", "朗读被系统拒绝：$text")
            onUtteranceFinished = null
            return false
        }
        EventLog.info("播报", text)
        return true
    }

    /** Stops the current utterance; the completion callback is not fired. */
    fun stop() {
        onUtteranceFinished = null
        pendingText = null
        pendingUtteranceId = null
        runCatching { engine?.stop() }
    }

    fun test(context: Context) {
        // A preview that comes out of the phone speaker is worse than no preview at all, so the
        // same gate the announcements go through applies here.
        if (!BluetoothMonitor.isRoutedToAirPods(context)) {
            EventLog.warn("播报", "未连接 AirPods，已跳过试听")
            return
        }
        stop()
        val settings = SettingsStore.state.value
        setTuning(settings.speechRate, settings.pitch)
        EventLog.info(
            "播报",
            "试听：语速 %.2f，音调 %.2f，引擎=%s，语言=%s".format(
                settings.speechRate,
                settings.pitch,
                runCatching { engine?.defaultEngine }.getOrNull() ?: "?",
                runCatching { engine?.voice?.locale?.toLanguageTag() }.getOrNull() ?: "?",
            ),
        )
        speak(context, "HyperPods 播报测试，一二三四五")
    }

    fun shutdown() {
        stop()
        abandonFocus()
        runCatching {
            engine?.stop()
            engine?.shutdown()
        }
        engine = null
        ready = false
        initializing = false
    }

    /** Lowers whatever is playing for the duration of the utterance, then restores it. */
    private fun requestDuckingFocus(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val request = focusRequest ?: AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { }
            .build()
            .also { focusRequest = it }
        runCatching { audioManager.requestAudioFocus(request) }
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        val context = appContext ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { audioManager.abandonAudioFocusRequest(request) }
    }

}
