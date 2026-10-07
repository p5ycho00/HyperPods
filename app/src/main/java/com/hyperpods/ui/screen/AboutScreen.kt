package com.hyperpods.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperpods.BuildConfig
import com.hyperpods.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Both rows of the developer card render their icon in a fixed 24dp box so that their text
 * columns start at the same x. [CaptionIndent] is the resulting inset of that text column:
 * the icon slot plus the 8dp gap BasicComponent inserts between the icon and the text.
 */
private val IconSlotWidth = 24.dp
private val IconSlotGap = 6.dp
private val CaptionIndent = IconSlotWidth + IconSlotGap + 8.dp

/**
 * The project repository, opened from the about tab.
 */
private const val ProjectUrl = "https://github.com/p5ycho00/HyperPods"

/**
 * The about tab: identity, appearance and credits.
 *
 * The version lives here and only here, so there is exactly one place to look it up.
 */
@Composable
fun AboutScreen(
    bottomPadding: Dp,
    onOpenTheme: () -> Unit,
    onOpenCredits: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = "关于",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onOpenTheme) {
                        Icon(imageVector = MiuixIcons.Theme, contentDescription = "主题设置")
                    }
                },
            )
        },
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
            item {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(76.dp),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "HyperPods",
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "版本 ${BuildConfig.VERSION_NAME}",
                            modifier = Modifier.padding(top = 4.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    BasicComponent(
                        title = "开发者",
                        summary = "DeepSeek V4 Flash",
                        startAction = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_deepseek),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = IconSlotGap)
                                    .size(IconSlotWidth),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        },
                        bottomAction = {
                            Text(
                                text = "模块设计与实现",
                                modifier = Modifier.padding(start = CaptionIndent),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                    )
                    BasicComponent(
                        title = "访问该项目",
                        summary = "打开本模块的 GitHub 仓库，查看更新日志、反馈问题与源码",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Community,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = IconSlotGap)
                                    .size(IconSlotWidth),
                            )
                        },
                        onClick = { uriHandler.openUri(ProjectUrl) },
                    )
                    ArrowPreference(
                        title = "引用与致谢",
                        summary = "本模块所用的开源项目",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Link,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = IconSlotGap)
                                    .size(IconSlotWidth),
                            )
                        },
                        onClick = onOpenCredits,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(bottomPadding + 16.dp)) }
        }
    }
}
