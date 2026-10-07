# HyperPods

一个 LSPosed 模块：**AirPods 连接期间，把系统通知朗读出来**，并且可以按应用勾选。
面向 HyperOS 4.0（Android 16）。

> 模块的设计与实现由 **DeepSeek V4 Flash** 完成。

## 它做什么

HyperOS 自身对 AirPods 的音频兼容已经可用，但通知播报不生效。本模块不依赖小米的耳机白名单，
而是自己做了一条完整的链路：

1. **检测连接**：Hook 蓝牙栈进程，捕获 AirPods 的 A2DP/HFP 连接与断开事件；
   同时保留一条应用层蓝牙监听的降级路径（需要「附近的设备」权限）。
2. **捕获通知**：模块内使用系统公开的 `NotificationListenerService` 读取通知，
   不侵入 system_server，因此不会因为 HyperOS 改动通知管线而失效。
3. **朗读**：用 TTS 以 `USAGE_MEDIA` 合成语音，保证声音从已连接的 AirPods 出来，
   而不是手机外放。

只有当「AirPods 已连接」+「通知播报开关打开」+「该应用已被勾选」三个条件同时成立时才会朗读。

## 界面

| 页面 | 内容 |
| --- | --- |
| 状态 | 连接状态、总开关、权限入口、模块激活状态 |
| 应用 | 可搜索的应用列表，勾选需要朗读的应用；顶部「全选 / 清空」 |
| 设置 | 朗读的应用、连续消息处理、仅在锁屏时播报、语音引擎、语速、音调、识别关键字 |
| 二级页 | 已选应用管理、设备识别关键字、运行日志、关于（miuix-nav 转场 + 侧滑返回） |

底部是带 Liquid Glass 效果的悬浮底栏：`miuix-blur` 做背景模糊与高光，
折射（lens）、饱和（vibrancy）、内阴影（inner shadow）来自 AndroidLiquidGlass 的实现，
结构（悬浮底栏 + 页面组织 + 二级页转场）沿用 KernelSU manager 的做法。

## 构建

需要 **JDK 21** 与 Android SDK。版本约束如下（比 HyperOS 4.0 的 API 36 更高，因为依赖库
声明了 `minCompileSdk = 37`；`compileSdk` 只影响编译期，不影响运行）：

| 配置 | 值 |
| --- | --- |
| minSdk | 31 |
| targetSdk | 36 |
| compileSdk | 37（minorApiLevel 0，即 `platforms;android-37.0`） |
| buildTools | 37.0.0 |
| JDK | 21 |

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（约 39MB）、
`app/build/outputs/apk/release/app-release-unsigned.apk`（约 24MB）。
用 Android Studio 直接打开本目录同样可以构建（Gradle 9.7.1 由 wrapper 自动下载）。

### 便携工具链（可选）

仓库里的 `.toolchain/` 是一套自包含的 JDK 21 + Android SDK，不依赖系统环境变量：

```bash
pwsh -File .toolchain/setup.ps1            # 首次：下载 JDK 与 Android SDK
pwsh -File .toolchain/build.ps1 :app:assembleDebug
```

`.toolchain/` 已被 `.gitignore` 忽略，约 2GB，不需要时直接整目录删除即可。

## 安装与启用

1. 安装 APK，在 LSPosed 里**启用 HyperPods 模块**。
2. 作用域**不用手动勾**：模块内置了推荐作用域，LSPosed 的作用域页面会把下面几个应用
   直接标为「推荐」并在你还没配置过时自动勾好；如果没自动勾，右上角菜单里有「使用推荐」一键应用。
   - `com.android.bluetooth`（小米机型可能叫 `com.xiaomi.bluetooth`）
   - `com.android.systemui`（只做播报类探测，不修改行为）

   也可以在「状态」页点「作用域与激活状态」让模块主动向框架申请，页面上会直接显示蓝牙是否已在作用域中。

   LSPosed 是在**目标进程启动时**注入的，而蓝牙进程开机就常驻，所以勾选作用域后需要**重启手机**
   才会生效。没重启时模块会自动退回「应用层检测」，功能可用，但 Hook 不会出现在日志里。
3. 打开 App，在「状态」页授权两项：
   - **通知使用权** —— 没有它就无法读取通知，这是朗读的前提。
   - **附近的设备** —— 供应用层降级检测使用。
4. 在「应用」页勾选需要朗读的应用。
5. 重启蓝牙或重新连接 AirPods；「运行日志」页可以看到实时记录。

## 工作原理

通知朗读这一层参考了 [kavishdevar/librepods](https://github.com/kavishdevar/librepods)
（同样跑在 HyperOS 上的 AirPods 项目）的实现，移植了它验证过的四个做法：

| 做法 | 为什么需要 |
| --- | --- |
| 优先读 `MessagingStyle` 的 `EXTRA_MESSAGES` | 聊天类通知的真实内容在这里；`EXTRA_TEXT` 往往只是「3 条新消息」 |
| 标题等于应用名时去掉标题 | 微信会把标题设成「微信」，否则会念两遍 |
| 清洗聚合通知的计数标记与重复发送者 | 形如「[3条]张三: 你好」的正文会被念成「你好」，而不是把计数和发送者一起念出来 |
| 每个会话只保留一条待播报 | 连发消息时朗读「第一条 + 最新一条」，而不是全部刷一遍 |
| 优先使用小爱语音引擎 | HyperOS 上 `com.xiaomi.mibrain.speech` 对语速/音调的支持最好，失败自动回落到系统默认 |

其中「Xiaomi TTS」需要在清单里声明 `<queries>`（Android 11+ 的包可见性），否则查询不到该引擎。

### 连续消息的两种处理方式

上一条还没念完时又来新消息，这是可配置的（设置 → 连续消息如何处理）：

| 模式 | 行为 | 取舍 |
| --- | --- | --- |
| **合并**（默认） | 「微信，还有 3 条新消息，最后一条：晚上见」 | 不会被悄悄丢消息，也不会越念越落后 |
| 逐条朗读全部 | 按到达顺序逐条念完 | 一条不漏，但消息密集时会落后于最新消息 |

默认选「合并」是因为它在两者之间：你明确知道刚才有几条没听到，同时听到的最新内容没有延迟。
如果你要每条都完整听到，把模式切成「逐条朗读全部」即可。

```
蓝牙栈进程                    模块 App 进程
┌─────────────────────┐      ┌───────────────────────────────┐
│ HyperPodsEntry      │      │ HyperPodsApp                  │
│  └ BluetoothHooks   │─广播→│  └ HookEventReceiver          │
│     (ContextImpl    │      │       └ ConnectionState       │
│      sendBroadcast  │      │                               │
│      重载)          │      │ NotificationReadoutService    │
└─────────────────────┘      │  └ ReadoutRouter              │
         ▲                   │       └ TtsSpeaker            │
         │ 远程偏好           └───────────────────────────────┘
         └────────── SettingsStore（模块 API 102 remote prefs）
```

- 配置写在框架的 **remote preferences** 里，App 写、Hook 读，两边永远一致。
- Hook 只挂 `ContextImpl` 的 `sendBroadcast*` 重载，不碰 A2DP 状态机内部类，
  所以在 AOSP 与 HyperOS 之间更抗改动。
- 广播带一次性 token，接收端在 Android 14+ 还会校验发送方 uid。

## 模块打包格式（API 100+）

libxposed 的现代模块**必须**在 APK 根目录带 `META-INF/xposed/` 文件，否则 LSPosed 会把它
当作旧的 `de.robv.android.xposed` 模块加载——那条路径要求入口类实现 `IXposedMod`，
不满足的类会被静默跳过，模块看起来装上却完全不工作。这三个文件在 `app/src/main/resources/` 下：

| 文件 | 作用 |
| --- | --- |
| `META-INF/xposed/java_init.list` | 入口类列表；**它的存在就是「这是现代模块」的判定依据** |
| `META-INF/xposed/module.prop` | `minApiVersion` / `targetApiVersion`，两个键都必须有 |
| `META-INF/xposed/scope.list` | 推荐作用域，每行一个包名 |

`scope.list` 由 LSPosed 管理器直接读取，用于标记「推荐」以及首次打开时预勾选。
注意它**不是** properties 文件：注释行和空行会被当成包名，所以只能写包名。

## 关于 HyperOS 自带播报

小米把「通知播报」限制在自家耳机上，但那个门禁类的名字是私有的，没有真机无法确定。
模块里的 `SystemReadoutProbe` 会在 SystemUI 中探测一组候选类名并写进运行日志——
把日志发出来就能定位到真正的拦截点，届时可以再加一层 Hook 让系统自带播报也支持 AirPods。
在那之前，模块自己实现的朗读链路是完整可用的。

## 已知限制 / 需要真机确认的点

- **蓝牙 Hook 点**：`ContextImpl.sendBroadcast*` 在理论上可能被 ART 内联而漏事件。
  因此应用层蓝牙监听是并行的第二条路径，两者共用同一份状态。
- **HID 之外的机型差异**：`com.xiaomi.bluetooth` 是否独立存在取决于 ROM 版本。
- **TTS 引擎**：依赖系统 TTS 与中文语音数据；若系统精简了语音包，日志里会给出提示。
- **编译状态**：debug 与 release 两个变体都已在本机实际编译通过
  （Gradle 9.7.1 / AGP 9.4.1 / Kotlin 2.4.20 / compileSdk 37），
  APK 内的 `assets/xposed_init`、LSPosed 元数据、`XposedProvider`、通知监听服务均已核对。
- **运行状态未验证**：检测点火时机、HyperOS 上蓝牙广播的实际 action 组合、TTS 走 A2DP
  的路由效果，都需要在真机上跑一遍才能确认——这些无法在没有设备的情况下验证。

## 性能与诊断

底栏的玻璃效果每帧要对整页内容做一次背景录制 + 模糊 + 折射着色。为了让它在长列表上依然跟手，
做了这几件事：

- 选中指示器在**静止时**是纯色药丸，只有手指按下才切换成真正的玻璃（两者在静止时像素一致，
  因为高光/折射/内阴影的强度都乘以了按压进度 0）。
- 应用图标按显示尺寸栅格化并做字节上限 LRU 缓存（自适应图标原始尺寸常是 432×432，
  按原始尺寸缓存每个约 750 KB）。
- 应用列表在启动时后台预热并缓存 5 分钟，避免第一次滑到「应用」页时才去读 200 多个包。
- 设置里可以整体关闭「底栏液态玻璃效果」，关掉后底栏变成纯色，最省电最流畅。

排查语音问题时有两个工具：

- 「语音引擎自检」：把同一句话按 0.5x 和 2.0x 各合成一次并比较时长，
  用来判断**引擎本身是否支持语速**，还是调用路径的问题。
- 试听按钮会记录实际生效的参数：`试听：语速 1.50，音调 1.20，引擎=…，语言=…`。

**语速/音调是语音引擎的能力，模块无法绕过。** 部分引擎（小米自带的那套就是）直接忽略标准
的参数接口。设置里的「语音引擎」可以切换设备上安装的其它引擎，切完会自动跑一次自检；
如果列表里只有「系统默认」，说明设备上没有第二个引擎，需要另外安装一个。

## 页面结构

| 页面 | 内容 |
| --- | --- |
| 状态（标签页） | 连接状态英雄卡（随状态变色）、朗读开关、必要权限 |
| 设置（标签页） | 全部配置：朗读的应用 / 播报内容 / 语音 / 设备识别 |
| 关于（标签页） | 版本、主题设置、引用与致谢 |
| 选择应用（二级页） | 搜索 + 勾选全部已安装应用，已选的应用排在最前，带全选/清空 |
| 设备识别关键字（二级页） | 增删设备名关键字 |
| 主题设置（二级页） | 色彩模式、动态取色、底栏液态玻璃 |
| 引用与致谢（二级页） | 本模块所用的开源项目及链接 |

模块不再在应用内保存日志；诊断信息只写到系统日志（`adb logcat -s HyperPods`），不占内存也不落盘。

以下行为是固定的，没有开关：

- 只在 AirPods 连接、且音频确实走在耳机上时才朗读（否则会从扬声器出声）
- 不播报应用名，只念会话与内容
- 常驻通知（音乐播放器、下载进度等）一律跳过
- 连接耳机时不播报提示音，只有通知会被朗读
- 播报时自动压低正在播放的音乐，播完立即恢复

已安装应用通常有几百个，所以已选的应用不再回显在设置页，只在选择页里排在列表最前面。

## 版本号

版本由 `version.properties` 驱动：每次构建（任何 Gradle 调用）`build` 自增一次，
`versionCode = build`，显示为 `major.minor.build`。版本号只在「关于」页顶部展示一处。

## 依赖

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| `io.github.libxposed:api` | 102.0.0 | 模块 API 102 |
| `top.yukonga.miuix.kmp:miuix-ui / -preference / -nav / -icons / -blur / -shader / -squircle` | 0.9.4 | UI 框架、偏好项、二级页转场、模糊与高光 |
| AGP / Kotlin / Compose | 9.4.1 / 2.4.20 / 2026.09.00 | 构建链 |

## 作者

模块的设计与实现由 **DeepSeek V4 Flash** 完成。

## 许可与致谢

`ui/component/liquid/` 与 `ui/component/animation/` 下的文件移植自
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（Apache 2.0），
悬浮底栏的结构参考 [tiann/KernelSU](https://github.com/tiann/KernelSU) 与
[compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) 的示例。
