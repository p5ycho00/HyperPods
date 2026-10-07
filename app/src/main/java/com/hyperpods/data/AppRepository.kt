package com.hyperpods.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.hyperpods.core.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

object AppRepository {

    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    @Volatile
    private var cached: List<AppEntry>? = null

    @Volatile
    private var cachedAt = 0L

    private val warmUpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Reads the list ahead of time. Building it costs a package-manager round trip plus a label
     * lookup per app, which is slow enough to be visible as a hitch when the apps page is first
     * swiped into view.
     */
    fun warmUp(context: Context) {
        val appContext = context.applicationContext
        warmUpScope.launch { loadInstalled(appContext) }
    }

    /** Packages that can never post user-facing notifications; hidden to keep the list usable. */
    private val HIDDEN_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.providers.settings",
        "com.android.shell",
        "com.hyperpods",
    )

    suspend fun loadInstalled(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val snapshot = cached
        if (snapshot != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            return@withContext snapshot
        }
        val packageManager = context.packageManager
        val collator = Collator.getInstance(Locale.CHINA)
        val loaded = runCatching {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName !in HIDDEN_PACKAGES }
                .map { info -> info.toEntry(packageManager) }
                .sortedWith(
                    compareBy<AppEntry> { it.isSystem }
                        .thenComparator { a, b -> collator.compare(a.label, b.label) },
                )
                .toList()
        }.getOrElse {
            EventLog.error("应用", "读取应用列表失败", it)
            emptyList()
        }
        if (loaded.isNotEmpty()) {
            cached = loaded
            cachedAt = System.currentTimeMillis()
        }
        loaded
    }

    private fun ApplicationInfo.toEntry(packageManager: PackageManager): AppEntry {
        val system = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val label = runCatching { packageManager.getApplicationLabel(this).toString() }
            .getOrDefault(packageName)
        return AppEntry(packageName = packageName, label = label, isSystem = system)
    }
}
