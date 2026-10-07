package com.hyperpods.ui.nav

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * Navigation graph. miuix-nav persists the back stack by serialising route instances, so every
 * key must be `@Serializable` and value-stable (data objects qualify).
 */
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object AppPicker : Route

    @Serializable
    data object Keywords : Route

    @Serializable
    data object Theme : Route

    @Serializable
    data object Credits : Route
}
