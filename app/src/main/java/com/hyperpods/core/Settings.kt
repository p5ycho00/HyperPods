package com.hyperpods.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * What happens when several notifications from one conversation arrive while the previous one is
 * still being spoken.
 */
enum class AnnouncementMode {
    /** Count them and read the latest: "微信，还有 3 条新消息，最后一条：…". Nothing is lost. */
    Merge,

    /** Read every message in order. Precise, but can lag behind a long burst. */
    All,

    ;

    val key: String get() = name

    companion object {
        fun fromKey(value: String?): AnnouncementMode =
            entries.firstOrNull { it.name == value } ?: Merge
    }
}

/**
 * Theme modes, mirroring KernelSU's numbering so the values stay interchangeable:
 * 0 system, 1 light, 2 dark, 3/4/5 the same three with dynamic (Monet) colour, 6 dark AMOLED.
 */
enum class ThemeMode(val value: Int) {
    System(0),
    Light(1),
    Dark(2),
    MonetSystem(3),
    MonetLight(4),
    MonetDark(5),
    DarkAmoled(6),
    ;

    val isMonet: Boolean get() = value >= 3
    val isDark: Boolean get() = value == 2 || value == 5 || value == 6
    val isSystem: Boolean get() = value == 0 || value == 3

    /** Base index used by the three-way mode selector. */
    val baseIndex: Int get() = if (isMonet) value - 3 else value

    fun withMonet(monet: Boolean): ThemeMode = when {
        monet && !isMonet -> when (this) {
            System, MonetSystem -> MonetSystem
            Light, MonetLight -> MonetLight
            else -> MonetDark
        }

        !monet && isMonet -> when (this) {
            MonetSystem -> System
            MonetLight -> Light
            else -> Dark
        }

        else -> this
    }

    companion object {
        fun fromValue(value: Int): ThemeMode = entries.firstOrNull { it.value == value } ?: MonetSystem
    }
}

data class Settings(
    val enabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val enabledPackages: Set<String> = emptySet(),
    val keywords: Set<String> = DEFAULT_KEYWORDS.toSet(),
    val appleOuiFallback: Boolean = false,
    val announceOnlyWhenLocked: Boolean = false,
    val announcementMode: AnnouncementMode = AnnouncementMode.Merge,
    val glassEffects: Boolean = true,
    /** Speech engine package, or empty for the system default. */
    val ttsEngine: String = "",
    val themeMode: ThemeMode = ThemeMode.MonetSystem,
)

/**
 * Single source of truth for module configuration.
 *
 * Values are mirrored into the Xposed framework's remote preferences once the service is bound,
 * which is how the hook running inside the Bluetooth process reads them. Until then they live in
 * a normal local [SharedPreferences] file so the UI keeps working before the module is activated.
 */
object SettingsStore {
    private const val LOCAL_FILE = "hyperpods_settings"

    private var appContext: Context? = null
    private var localPrefs: SharedPreferences? = null

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingPersist: Runnable? = null

    private val _remoteAvailable = MutableStateFlow(false)
    val remoteAvailable: StateFlow<Boolean> = _remoteAvailable.asStateFlow()

    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    private val _scope = MutableStateFlow<Set<String>>(emptySet())

    /** Packages the user has enabled in the LSPosed scope, as reported by the framework. */
    val scope: StateFlow<Set<String>> = _scope.asStateFlow()

    private val _frameworkInfo = MutableStateFlow("")

    /** "LSPosed 1.9.x (API 102)，能力：系统进程/远程偏好" once the service is bound. */
    val frameworkInfo: StateFlow<String> = _frameworkInfo.asStateFlow()

    /** Non-null once the Xposed framework has handed us its service binder. */
    @Volatile
    var service: XposedService? = null
        private set

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        localPrefs = context.applicationContext.getSharedPreferences(LOCAL_FILE, Context.MODE_PRIVATE)
        load()
    }

    /** Called when the framework hands us its service binder. */
    fun onServiceBound(service: XposedService) {
        val prefs = runCatching { service.getRemotePreferences(PREFS_GROUP) }.getOrNull()
        if (prefs == null) {
            EventLog.warn("Prefs", "框架不支持远程偏好，配置仅在模块内生效")
            return
        }
        remotePrefs = prefs
        this.service = service
        _remoteAvailable.value = true
        EventLog.info("Prefs", "已连接 Xposed 服务：${runCatching { service.frameworkName }.getOrNull() ?: "?"}")
        describeFramework(service)
        refreshScope()
        // Push whatever we have locally, then keep the fresher of the two.
        persist(_state.value)
        load()
    }

    fun onServiceDied() {
        _remoteAvailable.value = false
        remotePrefs = null
        service = null
        _scope.value = emptySet()
        EventLog.warn("Prefs", "Xposed 服务已断开")
    }

    /** Re-reads the module scope; call after a scope request completes. */
    fun refreshScope() {
        val current = service ?: return
        runCatching { current.scope }
            .onSuccess { _scope.value = it.toSet() }
            .onFailure { EventLog.warn("作用域", "读取作用域失败：${it.message}") }
    }

    /**
     * Records what the framework says about itself. `PROP_CAP_SYSTEM` matters here: without it the
     * framework cannot inject into system processes at all, which is the only way the Bluetooth
     * process hook could ever work.
     */
    private fun describeFramework(service: XposedService) {
        val name = runCatching { service.frameworkName }.getOrNull() ?: "?"
        val version = runCatching { service.frameworkVersion }.getOrNull() ?: "?"
        val api = runCatching { service.apiVersion }.getOrNull() ?: -1
        val properties = runCatching { service.frameworkProperties }.getOrNull() ?: 0L
        val capabilities = buildList {
            if (properties and XposedService.PROP_CAP_SYSTEM != 0L) add("系统进程")
            if (properties and XposedService.PROP_CAP_REMOTE != 0L) add("远程偏好")
        }
        val summary = "$name $version（API $api），能力：" +
            capabilities.ifEmpty { listOf("无") }.joinToString("/")
        _frameworkInfo.value = summary
        EventLog.info("框架", summary)
    }

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        _state.value = next
        persist(next)
    }

    /**
     * Like [update], but the state changes immediately while the write to the framework's remote
     * preferences is coalesced. Sliders fire dozens of times per gesture and every remote write is
     * a binder call.
     */
    fun updateDebounced(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        _state.value = next
        pendingPersist?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            pendingPersist = null
            persist(_state.value)
        }
        pendingPersist = runnable
        mainHandler.postDelayed(runnable, 300L)
    }

    /** Commits a pending debounced write right away. */
    fun flush() {
        val runnable = pendingPersist ?: return
        mainHandler.removeCallbacks(runnable)
        pendingPersist = null
        persist(_state.value)
    }

    private fun store(): SharedPreferences? = remotePrefs ?: localPrefs

    private fun load() {
        val prefs = store() ?: return
        val existingToken = prefs.getString(KEY_TOKEN, null)
        val token = existingToken ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_TOKEN, it).apply()
        }
        _token.value = token

        val defaults = Settings()
        _state.value = Settings(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            speechRate = prefs.getFloat(KEY_SPEECH_RATE, defaults.speechRate),
            pitch = prefs.getFloat(KEY_PITCH, defaults.pitch),
            enabledPackages = prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet(),
            keywords = prefs.getStringSet(KEY_KEYWORDS, defaults.keywords) ?: defaults.keywords,
            appleOuiFallback = prefs.getBoolean(KEY_APPLE_FALLBACK, defaults.appleOuiFallback),
            announceOnlyWhenLocked = prefs.getBoolean(
                KEY_ANNOUNCE_ONLY_LOCKED,
                defaults.announceOnlyWhenLocked,
            ),
            announcementMode = AnnouncementMode.fromKey(
                prefs.getString(KEY_ANNOUNCEMENT_MODE, null),
            ),
            glassEffects = prefs.getBoolean(KEY_GLASS_EFFECTS, defaults.glassEffects),
            ttsEngine = prefs.getString(KEY_TTS_ENGINE, defaults.ttsEngine) ?: defaults.ttsEngine,
            themeMode = ThemeMode.fromValue(
                prefs.getInt(KEY_THEME_MODE, defaults.themeMode.value),
            ),
        )
        if (existingToken == null) persist(_state.value)
    }

    private fun persist(settings: Settings) {
        val editorList = listOfNotNull(remotePrefs, localPrefs)
        if (editorList.isEmpty()) return
        for (prefs in editorList) {
            prefs.edit()
                .putInt(KEY_SCHEMA, PREFS_SCHEMA)
                .putString(KEY_TOKEN, _token.value)
                .putBoolean(KEY_ENABLED, settings.enabled)
                .putFloat(KEY_SPEECH_RATE, settings.speechRate)
                .putFloat(KEY_PITCH, settings.pitch)
                .putStringSet(KEY_PACKAGES, settings.enabledPackages)
                .putStringSet(KEY_KEYWORDS, settings.keywords)
                .putBoolean(KEY_APPLE_FALLBACK, settings.appleOuiFallback)
                .putBoolean(KEY_ANNOUNCE_ONLY_LOCKED, settings.announceOnlyWhenLocked)
                .putString(KEY_ANNOUNCEMENT_MODE, settings.announcementMode.key)
                .putBoolean(KEY_GLASS_EFFECTS, settings.glassEffects)
                .putString(KEY_TTS_ENGINE, settings.ttsEngine)
                .putInt(KEY_THEME_MODE, settings.themeMode.value)
                .apply()
        }
    }
}
