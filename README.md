# HyperPods

HyperOS 上连着 AirPods 的时候，把系统通知念给你听。一个 LSPosed 模块。

小米的「通知播报」只认自家耳机。AirPods 连上去能听歌、能接电话，消息却不念。这个模块补的就是这一段：自己读通知，自己决定什么时候出声，不依赖小米那套耳机白名单。

## 用起来

1. 装 APK，到 LSPosed 里启用 HyperPods。作用域不用手动勾——模块带了推荐作用域，管理器会把 `com.android.bluetooth`（小米机型上可能叫 `com.xiaomi.bluetooth`）直接标成推荐并预先勾好。
2. 重启手机。LSPosed 是在目标进程启动时注入的，而蓝牙进程开机就常驻，不重启的话模块只能退回应用层检测，功能能用，但 Hook 不会生效。
3. 打开应用，在「状态」页把两个权限给了：**通知使用权**（不给就读不到通知）和**附近的设备**（不靠 Hook 时用它检测连接）。
4. 到「设置」页点「选择要朗读的应用」，勾上想听的那几个。

## 设置项

「状态」页只有三样东西：连接状态、总开关、两个权限。

「设置」页分四组：

- **朗读的应用** —— 选择要朗读的应用（二级页，已选的应用排在最前面）
- **播报内容** —— 连续消息处理（合并 / 逐条）、仅在锁屏时播报
- **语音** —— 语音引擎、语速、音调、试听当前语音、语音引擎自检
- **设备识别** —— 设备识别关键字（二级页）、把 Apple 音频设备也视为 AirPods

「关于」页放版本号、开发者、访问该项目和引用与致谢，右上角齿轮进主题设置（色彩模式、动态取色、底栏液态玻璃效果）。

连着来好几条消息时默认用「合并」：上一条还没念完，就换成「微信，还有 3 条新消息，最后一条：…」。这样既不会悄悄丢消息，也不会越念越落后。要每条都完整听到，切成「逐条」。

## 做成固定行为、没给开关的地方

- 只在音频确实走在 AirPods 上时才朗读。没连耳机也念，声音就从手机外放出来了，不如不念。
- 不念应用名，只念标题和正文。
- 常驻通知一路跳过：音乐播放器、下载进度、导航这类。
- 连上耳机不播提示音，只念通知。
- 朗读时把正在放的音乐压低，念完立刻恢复。

## 踩过的坑

记几条踩过才知道的，省得以后再踩：

**模块打包格式。** libxposed API 100+ 的模块必须在 APK 里带 `META-INF/xposed/` 下的三个文件（`java_init.list`、`module.prop`、`scope.list`）。少了 `java_init.list`，LSPosed 会按老的 `de.robv.android.xposed` 模块去加载，入口类因为不满足 `IXposedMod` 被静默跳过——表现就是「模块装了、也启用了，但什么动静都没有」。另外 `module.prop` 里 `minApiVersion` 和 `targetApiVersion` 两个键都得有，少一个是管理器崩溃。

**TTS 走哪条音频流。** 默认的 `USAGE_ASSISTANT` 可能从手机外放出声，得用 `USAGE_MEDIA` / `USAGE_ASSISTANCE_ACCESSIBILITY` 才会跟着 A2DP 走。小米的引擎更倔，不管 audio attributes 怎么写都往音乐流上放，而 MIUI 一旦发现空闲的音乐流被占用就弹音量条，所以还额外指定了 `STREAM_ACCESSIBILITY`。顺带一提，要在 Android 11+ 上查到小米的语音引擎，清单里得声明 `<queries>`，否则包可见性查不到它。

**连发消息的内容在哪。** 微信这类应用的通知，真正的内容在 `MessagingStyle` 的 `EXTRA_MESSAGES` 里，`EXTRA_TEXT` 往往只有一句「3 条新消息」。正文里还会带上 `[3条]` 这种计数和重复的发送者，念之前得清掉，不然听着很怪。

**状态提示只是参考。** 注入状态在部分机型上会显示「无记录」，但朗读能不能用取决于音频有没有真的走在耳机上，跟这个提示没关系。

## 构建

需要 JDK 21 和 Android SDK。`compileSdk` 是 37、buildTools 37.0.0，比运行时的 API 36 高，那只是依赖库的编译要求，不影响运行；`minSdk` 31，Android 12 及以上都能装。

```bash
./gradlew :app:assembleDebug     # 产物在 app/build/outputs/apk/debug/
```

仓库里的 `.toolchain/` 是一套自带 JDK 和 Android SDK 的便携工具链，不依赖系统环境变量，也不需要装 Android Studio：

```bash
pwsh -File .toolchain/setup.ps1                       # 第一次用，先把 JDK 和 SDK 拉下来
pwsh -File .toolchain/build.ps1 :app:assembleDebug
```

版本号由 `version.properties` 驱动，每次构建自动加一，`versionCode` 就是这个数字，界面里显示成 `major.minor.build`。版本号只在「关于」页露一次面。

## 已知问题

**语速、音调调了没反应。** 这是语音引擎的问题，不是模块的：有些引擎（包括小米自带的某些版本）直接忽略标准接口。设置里的「语音引擎自检」会把同一句话按 0.5x 和 2.0x 各合成一遍，比对两次的时长，用数据判断是引擎不听，还是调用路径不对。换个引擎通常就好了。

**音量条偶尔冒出来。** 亮屏收到通知时，MIUI 偶尔还是弹一次音量条；正在放音乐的时候不会弹。暂时没找到干净的绕法。

**连接检测有两条路。** Hook 蓝牙进程（需要重启，日志里可能显示「无记录」）和应用层监听（需要「附近的设备」权限）。两条路共用同一份连接状态，互为兜底。

## 日志

应用内不留日志，诊断信息只写到系统日志：

```bash
adb logcat -s HyperPods
```

## 作者与致谢

模块的设计与实现由 **DeepSeek V4 Flash** 完成。

界面用的是 [miuix](https://github.com/compose-miuix-ui/miuix)：UI 框架、偏好项和二级页转场都来自它。悬浮底栏的结构参考了 [KernelSU](https://github.com/tiann/KernelSU)。液态玻璃的镜面高光、折射和内阴影移植自 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（Apache 2.0，对应文件在 `ui/component/liquid/` 和 `ui/component/animation/` 下）。通知内容的提取方式参考了 [librepods](https://github.com/kavishdevar/librepods) 在 HyperOS 上的做法。模块框架是 [libxposed](https://github.com/libxposed)，API 102。
