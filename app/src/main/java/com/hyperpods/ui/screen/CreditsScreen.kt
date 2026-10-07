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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private data class Credit(val name: String, val purpose: String, val url: String)

private val CREDITS = listOf(
    Credit(
        name = "compose-miuix-ui/miuix",
        purpose = "UI 框架、偏好项与二级页转场",
        url = "https://github.com/compose-miuix-ui/miuix",
    ),
    Credit(
        name = "Kyant0/AndroidLiquidGlass",
        purpose = "镜面高光、折射与内阴影",
        url = "https://github.com/Kyant0/AndroidLiquidGlass",
    ),
    Credit(
        name = "tiann/KernelSU",
        purpose = "悬浮底栏、页面结构与主题设置",
        url = "https://github.com/tiann/KernelSU",
    ),
    Credit(
        name = "libxposed/api",
        purpose = "模块 API 102",
        url = "https://github.com/libxposed",
    ),
)

/** Secondary page: the projects this module is built on, with links to each repository. */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "引用与致谢",
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
                    text = "本模块站在这些项目之上，点击可跳转到对应仓库。",
                    modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            item {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    CREDITS.forEach { credit ->
                        ArrowPreference(
                            title = credit.name,
                            summary = credit.purpose,
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Link,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            onClick = { uriHandler.openUri(credit.url) },
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
