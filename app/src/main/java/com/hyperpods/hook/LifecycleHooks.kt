package com.hyperpods.hook

import android.content.Context
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Reports that the module really is running inside the target process.
 *
 * Without this, "the hook was injected but never saw a Bluetooth broadcast" and "the hook was never
 * injected at all" look identical from the app's side — both produce an empty log. Hooking
 * [ContextWrapper.attachBaseContext] gives us a Context as soon as the process starts, which is
 * both the earliest moment we can talk to the module app and a reliable heartbeat.
 */
internal object LifecycleHooks {

    fun install(
        module: XposedModule,
        param: XposedModuleInterface.PackageLoadedParam,
        bridge: HookBridge,
    ) {
        val classLoader = param.defaultClassLoader
        val attach = runCatching {
            val contextWrapper = Class.forName("android.content.ContextWrapper", false, classLoader)
            contextWrapper.getDeclaredMethod("attachBaseContext", Context::class.java)
        }.getOrElse {
            bridge.warn("Hook", "找不到 ContextWrapper.attachBaseContext，无法上报注入状态")
            return
        }

        var announced = false
        runCatching {
            module.hook(attach)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    runCatching {
                        val base = chain.getArg(0) as? Context
                        if (base != null) {
                            bridge.rememberContext(base.applicationContext ?: base)
                            if (!announced) {
                                announced = true
                                bridge.log("Hook", "已注入 ${param.packageName}，进程已就绪")
                            }
                        }
                    }
                    chain.proceed()
                }
        }.onFailure { bridge.warn("Hook", "挂载注入心跳失败：${it.message}") }
    }
}
