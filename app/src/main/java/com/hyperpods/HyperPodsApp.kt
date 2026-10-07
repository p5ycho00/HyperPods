package com.hyperpods

import android.app.Application
import com.hyperpods.core.EventLog
import com.hyperpods.core.SettingsStore
import com.hyperpods.data.AppRepository
import com.hyperpods.readout.ReadoutController
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class HyperPodsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        ReadoutController.onStart(this)
        AppRepository.warmUp(this)
        registerXposedService()
        EventLog.info("模块", "HyperPods 已启动")
    }

    /**
     * The framework calls into our `XposedProvider` with its service binder; from there we get
     * remote preferences (shared with the hook) and the scope helpers.
     */
    private fun registerXposedService() {
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    SettingsStore.onServiceBound(service)
                }

                override fun onServiceDied(service: XposedService) {
                    SettingsStore.onServiceDied()
                }
            })
        }.onFailure { EventLog.warn("模块", "注册 Xposed 服务失败：${it.message}") }
    }
}
