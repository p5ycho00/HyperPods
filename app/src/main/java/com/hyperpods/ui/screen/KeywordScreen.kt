package com.hyperpods.ui.screen

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperpods.core.SettingsStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// Secondary page: the device-name keywords that mark a Bluetooth device as an AirPod.
// Useful for rebranded clones whose product name differs from AirPods.
@Composable
fun KeywordScreen(onBack: () -> Unit) {
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val inputState = rememberTextFieldState()

    val keywords = remember(settings.keywords) { settings.keywords.sorted() }

    fun addKeyword() {
        val value = inputState.text.toString().trim().lowercase()
        if (value.isEmpty()) return
        SettingsStore.update { it.copy(keywords = it.keywords + value) }
        inputState.setTextAndPlaceCursorAtEnd("")
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "设备识别关键字",
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
                Text(
                    text = "设备名称（不区分大小写）中包含任一关键字时，就按 AirPods 处理。",
                    modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            item {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextField(
                            state = inputState,
                            modifier = Modifier.weight(1f),
                            label = "新增关键字",
                            useLabelAsPlaceholder = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                        TextButton(text = "添加", onClick = { addKeyword() })
                    }
                }
            }
            items(keywords.chunked(8)) { chunk ->
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    chunk.forEach { keyword ->
                        ArrowPreference(
                            title = keyword,
                            summary = "点击移除",
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Ok,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            onClick = {
                                SettingsStore.update { it.copy(keywords = it.keywords - keyword) }
                            },
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
