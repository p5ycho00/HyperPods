package com.hyperpods.ui.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperpods.core.AnnouncementMode
import com.hyperpods.core.BluetoothMonitor
import com.hyperpods.core.ConnectionState
import com.hyperpods.core.SettingsStore
import com.hyperpods.readout.TtsEngines
import com.hyperpods.readout.TtsSelfCheck
import com.hyperpods.readout.TtsSpeaker
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val ANNOUNCEMENT_MODE_LABELS = listOf(
    "合并：还有 N 条新消息",
    "逐条朗读全部",
)

private fun announcementModeIndex(mode: AnnouncementMode): Int = when (mode) {
    AnnouncementMode.Merge -> 0
    AnnouncementMode.All -> 1
}

/**
 * The settings tab.
 *
 * Everything that configures the module lives here, grouped by what it affects: which apps are
 * read, what gets said, how it sounds, how the headset is recognised, and the interface itself.
 */
@Composable
fun SettingsScreen(
    bottomPadding: Dp,
    onOpenPicker: () -> Unit,
    onOpenKeywords: () -> Unit,
) {
    val context = LocalContext.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val connection by ConnectionState.state.collectAsStateWithLifecycle()
    // Speaking while nothing is routed to the AirPods would play through the phone speaker, so the
    // preview entry is disabled until the same gate the announcements use says it is safe.
    val canPreview = remember(connection) { BluetoothMonitor.isRoutedToAirPods(context) }
    val scrollBehavior = MiuixScrollBehavior()
    val engines = remember { TtsEngines.installed(context) }

    Scaffold(
        topBar = { TopAppBar(title = "设置", scrollBehavior = scrollBehavior) },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
        ) {
            // ---------------------------------------------------------------- 朗读的应用
            item { SmallTitle(text = "朗读的应用", modifier = Modifier.padding(top = 8.dp)) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "选择要朗读的应用",
                        summary = if (settings.enabledPackages.isEmpty()) {
                            null
                        } else {
                            "已选 ${settings.enabledPackages.size} 个"
                        },
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.SelectAll,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onClick = onOpenPicker,
                    )
                }
            }
            // ---------------------------------------------------------------- 播报内容
            item { SmallTitle(text = "播报内容", modifier = Modifier.padding(top = 16.dp)) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(
                        items = ANNOUNCEMENT_MODE_LABELS,
                        selectedIndex = announcementModeIndex(settings.announcementMode),
                        title = "连续消息处理",
                        summary = "上一条还没念完时又来新消息",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Messages,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onSelectedIndexChange = { index ->
                            SettingsStore.update {
                                it.copy(
                                    announcementMode = when (index) {
                                        1 -> AnnouncementMode.All
                                        else -> AnnouncementMode.Merge
                                    },
                                )
                            }
                        },
                    )
                    SwitchPreference(
                        checked = settings.announceOnlyWhenLocked,
                        onCheckedChange = { value ->
                            SettingsStore.update { it.copy(announceOnlyWhenLocked = value) }
                        },
                        title = "仅在锁屏时播报",
                        summary = "关闭时，亮屏使用中也会朗读",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Lock,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                    )
                }
            }

            // ---------------------------------------------------------------- 语音
            item { SmallTitle(text = "语音", modifier = Modifier.padding(top = 16.dp)) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(
                        items = engines.map { it.label },
                        selectedIndex = engines
                            .indexOfFirst {
                                it.packageName.equals(settings.ttsEngine.ifBlank { null }, true)
                            }
                            .coerceAtLeast(0),
                        title = "语音引擎",
                        summary = if (engines.size <= 1) {
                            "只检测到一个引擎；若它忽略语速，需要另外安装一个语音引擎"
                        } else {
                            "不同引擎对语速、音调的支持不同，可逐个自检"
                        },
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Mic,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onSelectedIndexChange = { index ->
                            engines.getOrNull(index)?.let { engine ->
                                SettingsStore.update { it.copy(ttsEngine = engine.packageName.orEmpty()) }
                                TtsSpeaker.reinitialize(context)
                                TtsSelfCheck.run(context)
                            }
                        },
                    )
                    SliderPreference(
                        value = settings.speechRate,
                        onValueChange = { value ->
                            // Apply immediately so the very next utterance uses it, and persist
                            // with a debounce (a binder write per frame would be wasteful).
                            SettingsStore.updateDebounced { it.copy(speechRate = value) }
                            TtsSpeaker.setTuning(value, SettingsStore.state.value.pitch)
                        },
                        onValueChangeFinished = { SettingsStore.flush() },
                        title = "语速",
                        summary = "%.2f 倍".format(settings.speechRate),
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Forward,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                    )
                    SliderPreference(
                        value = settings.pitch,
                        onValueChange = { value ->
                            SettingsStore.updateDebounced { it.copy(pitch = value) }
                            TtsSpeaker.setTuning(SettingsStore.state.value.speechRate, value)
                        },
                        onValueChangeFinished = { SettingsStore.flush() },
                        title = "音调",
                        summary = "%.2f".format(settings.pitch),
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Music,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                    )
                    ArrowPreference(
                        title = "试听当前语音",
                        summary = if (canPreview) {
                            "会通过当前连接的耳机播放"
                        } else {
                            "未连接 AirPods，暂时不能试听"
                        },
                        enabled = canPreview,
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Play,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onClick = { TtsSpeaker.test(context) },
                    )
                    ArrowPreference(
                        title = "语音引擎自检",
                        summary = "实测当前引擎是否支持语速调节，结果写入系统日志",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Scan,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onClick = { TtsSelfCheck.run(context) },
                    )
                }
            }

            // ---------------------------------------------------------------- 设备识别
            item { SmallTitle(text = "设备识别", modifier = Modifier.padding(top = 16.dp)) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "设备识别关键字",
                        summary = "当前 ${settings.keywords.size} 个关键字",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.SearchDevice,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onClick = onOpenKeywords,
                    )
                    SwitchPreference(
                        checked = settings.appleOuiFallback,
                        onCheckedChange = { value ->
                            SettingsStore.update { it.copy(appleOuiFallback = value) }
                        },
                        title = "把 Apple 音频设备也视为 AirPods",
                        summary = "改名后名称里没有 AirPods 时，用 Apple 网卡前缀兜底",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Replace,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(bottomPadding + 16.dp)) }
        }
    }
}
