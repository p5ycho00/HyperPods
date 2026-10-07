package com.hyperpods.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperpods.core.SettingsStore
import com.hyperpods.core.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun HyperPodsTheme(content: @Composable () -> Unit) {
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val dark = if (settings.themeMode.isSystem) {
        isSystemInDarkTheme()
    } else {
        settings.themeMode.isDark
    }
    val colorSchemeMode = when (settings.themeMode) {
        ThemeMode.System -> ColorSchemeMode.System
        ThemeMode.Light -> ColorSchemeMode.Light
        ThemeMode.Dark, ThemeMode.DarkAmoled -> ColorSchemeMode.Dark
        ThemeMode.MonetSystem -> ColorSchemeMode.MonetSystem
        ThemeMode.MonetLight -> ColorSchemeMode.MonetLight
        ThemeMode.MonetDark -> ColorSchemeMode.MonetDark
    }
    // The palette stays at the tonal-spot / 2025 defaults: no custom seed is exposed any more, so
    // "dynamic colour" simply follows the wallpaper and everything else uses the built-in palette.
    val controller = remember(colorSchemeMode, dark) {
        ThemeController(
            colorSchemeMode = colorSchemeMode,
            keyColor = null,
            colorSpec = ThemeColorSpec.Spec2025,
            paletteStyle = ThemePaletteStyle.TonalSpot,
            isDark = dark,
        )
    }
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            content = content,
        )
    }
}

/** Follows the user's theme mode rather than the raw system setting. */
@Composable
fun isInDarkTheme(): Boolean {
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    return if (settings.themeMode.isSystem) isSystemInDarkTheme() else settings.themeMode.isDark
}
