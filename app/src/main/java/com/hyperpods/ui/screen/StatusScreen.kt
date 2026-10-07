package com.hyperpods.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperpods.core.BluetoothMonitor
import com.hyperpods.core.ConnectionState
import com.hyperpods.core.EventLog
import com.hyperpods.core.SettingsStore
import com.hyperpods.readout.TtsSpeaker
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Settings as SettingsIcon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.hyperpods.ui.theme.isInDarkTheme

/** Everything the user needs to tell whether the module is working right now. */
@Composable
fun StatusScreen(bottomPadding: Dp) {
    val context = LocalContext.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()

    val notificationAccess = rememberNotificationAccess()
    var bluetoothPermission by remember { mutableStateOf(BluetoothMonitor.hasPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        bluetoothPermission = granted
        if (granted) BluetoothMonitor.refresh(context)
    }

    OnResume {
        bluetoothPermission = BluetoothMonitor.hasPermission(context)
        BluetoothMonitor.refresh(context)
    }

    Scaffold(
        topBar = { TopAppBar(title = "HyperPods", scrollBehavior = scrollBehavior) },
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
            item { ConnectionHero() }
            item {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    SwitchPreference(
                        checked = settings.enabled,
                        onCheckedChange = { value ->
                            SettingsStore.update { it.copy(enabled = value) }
                        },
                        title = "启用 HyperPods",
                        summary = "总开关，关闭后不检测也不朗读",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Ok,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                    )
                }
            }
            item {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    ArrowPreference(
                        title = "通知使用权",
                        summary = if (notificationAccess.value) "已授权" else "未授权，点击前往开启",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.SettingsIcon,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onClick = { context.openNotificationListenerSettings() },
                    )
                    ArrowPreference(
                        title = "附近的设备权限",
                        summary = if (bluetoothPermission) {
                            "已授权"
                        } else {
                            "未授权，点击授权（供模块自行检测连接）"
                        },
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.SearchDevice,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onClick = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(bottomPadding + 16.dp)) }
        }
    }
}

/**
 * The hero card, in the spirit of KernelSU's home screen: a tinted surface, a large watermark icon
 * bleeding out of the bottom-right corner, and the state itself in large type. The colour changes
 * with the state — connected uses the "working" container, disconnected a lighter accent.
 */
@Composable
private fun ConnectionHero() {
    val connection by ConnectionState.state.collectAsStateWithLifecycle()
    val connected = connection.connected
    // Same reds KernelSU uses for its error cards: the themed container when dynamic colour is on,
    // otherwise its fixed light/dark pair.
    val containerColor = if (connected) {
        MiuixTheme.colorScheme.secondaryContainer
    } else when {
        isDynamicColor -> MiuixTheme.colorScheme.errorContainer
        isInDarkTheme() -> Color(0xFF310808)
        else -> Color(0xFFF8E2E2)
    }
    // Black on both states in light mode; the dark theme's containers are dark red/green, so the
    // text flips to white there to stay readable.
    val contentColor = if (isInDarkTheme()) Color.White else Color.Black
    val iconTint = if (connected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
    } else {
        contentColor.copy(alpha = 0.35f)
    }

    Card(
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = containerColor, contentColor = contentColor),
        insideMargin = PaddingValues(0.dp),
    ) {
        // Clipped to the card's own radius: the watermark is meant to bleed off the corner, not
        // past the edge onto the page behind it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(CardDefaults.CornerRadius)),
        ) {
            Icon(
                imageVector = if (connected) MiuixIcons.Ok else MiuixIcons.SearchDevice,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 26.dp, y = 28.dp)
                    .size(124.dp),
            )
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 18.dp),
            ) {
                Text(
                    text = if (connected) "已连接" else "未连接",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                if (connected) {
                    connection.deviceName?.let { name ->
                        Text(
                            text = name,
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 15.sp,
                            color = contentColor,
                        )
                    }
                }
            }
            if (connected) {
                connection.deviceAddress?.let { address ->
                    Text(
                        text = address,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 18.dp),
                        fontSize = 13.sp,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberNotificationAccess(): State<Boolean> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(hasNotificationAccess(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = hasNotificationAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable
private fun OnResume(block: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) block()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

fun hasNotificationAccess(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
    ) ?: return false
    return enabled.split(':').any { it.contains(context.packageName) }
}

fun Context.openNotificationListenerSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { EventLog.warn("状态", "无法打开通知使用权设置：${it.message}") }
}
