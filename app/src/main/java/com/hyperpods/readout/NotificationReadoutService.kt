package com.hyperpods.readout

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hyperpods.core.BluetoothMonitor
import com.hyperpods.core.EventLog
import com.hyperpods.core.SettingsStore

/**
 * Reads notifications from every app the user allowed and hands them to [ReadoutRouter].
 *
 * Using the public notification-listener API (instead of hooking the notification service) keeps
 * the module away from system_server internals: the same readout works no matter how HyperOS
 * reshuffles its notification pipeline.
 */
class NotificationReadoutService : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        TtsSpeaker.ensure(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        EventLog.info("服务", "已获得通知使用权，通知朗读就绪")
        // The process may have just been started from cold; refresh what we missed.
        BluetoothMonitor.start(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        EventLog.warn("服务", "通知使用权已断开")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        runCatching { ReadoutRouter.handle(this, notification) }
            .onFailure { EventLog.error("播报", "处理通知失败", it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit
}
