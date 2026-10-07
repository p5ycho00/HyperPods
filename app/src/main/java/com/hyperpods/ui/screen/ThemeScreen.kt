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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperpods.core.SettingsStore
import com.hyperpods.core.ThemeMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val MODE_LABELS = listOf("跟随系统", "浅色", "深色")

/**
 * Theme settings: which colour mode to follow, whether to take colours from the wallpaper, and
 * whether the bottom bar keeps its liquid-glass treatment.
 */
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val mode = settings.themeMode

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "主题设置",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
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
            item {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    OverlayDropdownPreference(
                        items = MODE_LABELS,
                        selectedIndex = mode.baseIndex.coerceIn(0, MODE_LABELS.lastIndex),
                        title = "色彩模式",
                        summary = "选择跟随系统，或固定为浅色 / 深色",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Months,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onSelectedIndexChange = { index ->
                            val target = when (index) {
                                1 -> ThemeMode.Light
                                2 -> ThemeMode.Dark
                                else -> ThemeMode.System
                            }
                            SettingsStore.update { it.copy(themeMode = target.withMonet(mode.isMonet)) }
                        },
                    )
                    SwitchPreference(
                        checked = mode.isMonet,
                        onCheckedChange = { monet ->
                            SettingsStore.update { it.copy(themeMode = mode.withMonet(monet)) }
                        },
                        title = "动态取色",
                        summary = "从系统壁纸提取配色（Monet）",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Background,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                    )
                    SwitchPreference(
                        checked = settings.glassEffects,
                        onCheckedChange = { value ->
                            SettingsStore.update { it.copy(glassEffects = value) }
                        },
                        title = "底栏液态玻璃效果",
                        summary = "关闭后底栏改为纯色，滑动更省电、更流畅",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Layers,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
