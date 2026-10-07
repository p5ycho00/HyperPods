package com.hyperpods.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperpods.core.SettingsStore
import kotlinx.coroutines.launch
import com.hyperpods.ui.component.FloatingBottomBar
import com.hyperpods.ui.component.FloatingBottomBarItem
import com.hyperpods.ui.nav.Route
import com.hyperpods.ui.screen.AboutScreen
import com.hyperpods.ui.screen.AppPickerScreen
import com.hyperpods.ui.screen.CreditsScreen
import com.hyperpods.ui.screen.KeywordScreen
import com.hyperpods.ui.screen.SettingsScreen
import com.hyperpods.ui.screen.StatusScreen
import com.hyperpods.ui.screen.ThemeScreen
import com.hyperpods.ui.theme.HyperPodsTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HyperPodsTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val backStack = rememberNavBackStack<Route>(Route.Main)
    val navigator = remember(backStack) { NavController(backStack) }
    val backdropColor = MiuixTheme.colorScheme.surface
    val cornerRadius = rememberNavSystemCornerRadius()
    val effects = remember(backdropColor, cornerRadius) {
        NavDisplayEffects(
            cornerClipRadius = cornerRadius,
            dimAmount = 0.5f,
            backdropColor = backdropColor,
        )
    }
    val swipeBack = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        NavSwipeDirection.RightToLeft
    } else {
        NavSwipeDirection.LeftToRight
    }
    NavDisplay(
        backStack = backStack,
        onBack = { navigator.pop() },
        transition = NavTransitions.MiuixDefault,
        effects = effects,
    ) {
        entry<Route.Main>(swipeDismiss = swipeBack) {
            MainScreen(
                onOpenPicker = { navigator.push(Route.AppPicker) },
                onOpenKeywords = { navigator.push(Route.Keywords) },
                onOpenTheme = { navigator.push(Route.Theme) },
                onOpenCredits = { navigator.push(Route.Credits) },
            )
        }
        entry<Route.AppPicker>(swipeDismiss = swipeBack) {
            AppPickerScreen(onBack = { navigator.pop() })
        }
        entry<Route.Keywords>(swipeDismiss = swipeBack) {
            KeywordScreen(onBack = { navigator.pop() })
        }
        entry<Route.Theme>(swipeDismiss = swipeBack) {
            ThemeScreen(onBack = { navigator.pop() })
        }
        entry<Route.Credits>(swipeDismiss = swipeBack) {
            CreditsScreen(onBack = { navigator.pop() })
        }
    }
}

private data class BottomTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun MainScreen(
    onOpenPicker: () -> Unit,
    onOpenKeywords: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenCredits: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    // Compose the neighbouring pages only after the first frame has landed. The settings page is
    // the heaviest of the three, and composing it mid-swipe showed up as a stutter; preparing it
    // while idle keeps the swipe itself smooth.
    var pagesReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        pagesReady = true
    }

    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val tabs = remember {
        listOf(
            BottomTab("状态", MiuixIcons.Home),
            BottomTab("设置", MiuixIcons.Settings),
            BottomTab("关于", MiuixIcons.Info),
        )
    }

    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barBottomPadding = if (navigationBarInset != 0.dp) 8.dp + navigationBarInset else 28.dp

    Scaffold(
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                FloatingBottomBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 28.dp, end = 28.dp, bottom = barBottomPadding),
                    selectedIndex = pagerState.currentPage,
                    onSelected = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    backdrop = backdrop,
                    tabsCount = tabs.size,
                    isBlurEnabled = settings.glassEffects,
                ) { activateTab ->
                    tabs.forEachIndexed { index, tab ->
                        val selected = pagerState.currentPage == index
                        FloatingBottomBarItem(
                            selected = selected,
                            onClick = { activateTab(index) },
                            modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                        ) {
                            val tint = if (selected) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            }
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                            )
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = tint,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val bottomPadding = innerPadding.calculateBottomPadding()
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
                beyondViewportPageCount = if (pagesReady) 1 else 0,
            ) { page ->
                when (page) {
                    0 -> StatusScreen(bottomPadding = bottomPadding)

                    1 -> SettingsScreen(
                        bottomPadding = bottomPadding,
                        onOpenPicker = onOpenPicker,
                        onOpenKeywords = onOpenKeywords,
                    )

                    else -> AboutScreen(
                        bottomPadding = bottomPadding,
                        onOpenTheme = onOpenTheme,
                        onOpenCredits = onOpenCredits,
                    )
                }
            }
        }
    }
}
