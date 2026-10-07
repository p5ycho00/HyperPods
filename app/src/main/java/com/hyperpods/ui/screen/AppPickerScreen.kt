package com.hyperpods.ui.screen

import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperpods.core.EventLog
import com.hyperpods.core.SettingsStore
import com.hyperpods.data.AppEntry
import com.hyperpods.data.AppRepository
import com.hyperpods.ui.component.AppIcon
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Secondary page: search the installed apps and pick which ones may be read out loud. */
@Composable
fun AppPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        apps = AppRepository.loadInstalled(context)
        loading = false
    }

    val searchState = rememberTextFieldState()
    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }.collect { query = it }
    }

    val filtered = remember(apps, query, showSystem) {
        apps.filter { entry ->
            (showSystem || !entry.isSystem) &&
                (query.isBlank() ||
                    entry.label.contains(query, ignoreCase = true) ||
                    entry.packageName.contains(query, ignoreCase = true))
        }
    }
    // Already-selected apps stay at the top, in the repository's own collated order.
    val ordered = remember(filtered, settings.enabledPackages) {
        val selected = filtered.filter { it.packageName in settings.enabledPackages }
        val rest = filtered.filterNot { it.packageName in settings.enabledPackages }
        selected + rest
    }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "选择应用",
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
                TextField(
                    state = searchState,
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    label = "搜索应用或包名",
                    useLabelAsPlaceholder = true,
                )
            }
            if (settings.enabledPackages.isNotEmpty()) {
                item {
                    Text(
                        text = "已选 ${settings.enabledPackages.size} 个",
                        modifier = Modifier.padding(top = 10.dp, start = 8.dp, end = 8.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            item {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    SwitchPreference(
                        checked = showSystem,
                        onCheckedChange = { showSystem = it },
                        title = "显示系统应用",
                        summary = "系统应用默认隐藏，避免列表过长",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.GridView,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                    )
                }
            }
            if (loading) {
                item {
                    Text(
                        text = "正在读取应用列表…",
                        modifier = Modifier.padding(top = 16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            // One continuous list: grouping a few hundred apps into boxes of ten read as noise.
            items(ordered, key = { it.packageName }) { entry ->
                AppRow(
                    entry = entry,
                    checked = entry.packageName in settings.enabledPackages,
                    onCheckedChange = { checked ->
                        if (checked) {
                            SettingsStore.update { current ->
                                current.copy(
                                    enabledPackages = current.enabledPackages + entry.packageName,
                                )
                            }
                            EventLog.info("应用", "已勾选 ${entry.label}")
                        } else {
                            SettingsStore.update { current ->
                                current.copy(
                                    enabledPackages = current.enabledPackages - entry.packageName,
                                )
                            }
                        }
                    },
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AppRow(
    entry: AppEntry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    CheckboxPreference(
        title = entry.label,
        summary = entry.packageName,
        checked = checked,
        onCheckedChange = onCheckedChange,
        checkboxLocation = CheckboxLocation.End,
        startAction = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = entry.packageName)
            }
        },
    )
}
