package com.hyperpods.readout

import android.app.KeyguardManager
import android.app.Notification
import android.content.Context
import android.os.Parcelable
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import com.hyperpods.core.BluetoothMonitor
import com.hyperpods.core.ConnectionState
import com.hyperpods.core.EventLog
import com.hyperpods.core.MODULE_PACKAGE
import com.hyperpods.core.SettingsStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Decides whether an incoming notification should be spoken, and turns it into a sentence.
 *
 * Content extraction follows the shape real chat apps post: a `MessagingStyle` notification carries
 * the actual latest message (and its sender) in `EXTRA_MESSAGES`, while `EXTRA_TEXT` often holds
 * only a summary line such as "3 条新消息".
 */
object ReadoutRouter {

    private const val SUMMARY_WINDOW_MS = 2_500L

    /** groupKey -> when a message of that group was last handled. */
    private val recentGroups = ConcurrentHashMap<String, Long>()

    /** Notification categories that are never worth reading out loud. */
    private val EXCLUDED_CATEGORIES = setOf(
        Notification.CATEGORY_ALARM,
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_LOCATION_SHARING,
        Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_STOPWATCH,
        Notification.CATEGORY_SYSTEM,
        Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_WORKOUT,
    )

    fun handle(context: Context, sbn: StatusBarNotification) {
        SettingsStore.init(context)
        val settings = SettingsStore.state.value

        val notification = sbn.notification
        val packageName = sbn.packageName ?: return
        if (packageName == MODULE_PACKAGE) return
        if (!settings.enabled) return

        // Reading a notification out loud is only correct while the audio actually goes to the
        // AirPods: if it is routed anywhere else the phone speaker announces instead. So the
        // permission-free audio-output check is the gate, and only the hook — which sees the real
        // profile events — is trusted without it.
        val connection = ConnectionState.state.value
        val audioRoutedToAirPods = BluetoothMonitor.checkAirPodsAudioOutput(context)
        val hookSaysConnected = connection.connected && connection.source == "hook"
        if (!audioRoutedToAirPods && !hookSaysConnected) return

        if (packageName !in settings.enabledPackages) return
        if (settings.announceOnlyWhenLocked && !isLocked(context)) return
        if (ineligibilityReason(sbn) != null) return

        val now = System.currentTimeMillis()
        val groupKey = sbn.groupKey
        val isSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

        // Chat apps post the message and a group summary that repeats it. Dropping every summary
        // outright silences apps that only post the summary, so a summary is skipped only when a
        // message from the same group was handled moments ago.
        if (isSummary && groupKey != null) {
            val lastHandled = recentGroups[groupKey]
            if (lastHandled != null && now - lastHandled < SUMMARY_WINDOW_MS) return
        }

        val announcement = buildAnnouncement(context, sbn) ?: return
        EventLog.info("播报", announcement.content)
        if (groupKey != null) recentGroups[groupKey] = now
        Announcer.offer(context = context, announcement = announcement, dedupeKey = sbn.key)
    }

    private fun ineligibilityReason(sbn: StatusBarNotification): String? {
        val notification = sbn.notification
        return when {
            notification.visibility == Notification.VISIBILITY_SECRET -> "秘密级别的通知"
            notification.flags and Notification.FLAG_ONGOING_EVENT != 0 -> "常驻通知"
            !sbn.isClearable -> "不可清除的通知"
            notification.category in EXCLUDED_CATEGORIES -> "被排除的通知类型"
            else -> null
        }
    }

    private fun buildAnnouncement(context: Context, sbn: StatusBarNotification): PendingAnnouncement? {
        val notification = sbn.notification
        val extras = notification.extras
        val appName = appLabel(context, sbn.packageName)

        // Chat apps: the real content lives in the MessagingStyle history.
        val messages = runCatching {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java),
            )
        }.getOrNull()
        val latest = messages?.lastOrNull()
        if (latest != null) {
            val sender = latest.senderPerson?.name
                ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)
            val content = AnnouncementFormatter.format(appName, sender, latest.text) ?: return null
            return PendingAnnouncement(
                conversationKey = conversationKey(sbn, sender),
                content = content,
            )
        }

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
        val content = AnnouncementFormatter.format(appName, title, text) ?: return null
        return PendingAnnouncement(
            conversationKey = conversationKey(sbn, title),
            content = content,
        )
    }

    /** Groups messages from the same conversation so a burst collapses to the latest one. */
    private fun conversationKey(sbn: StatusBarNotification, fallbackTitle: CharSequence?): String {
        val notification = sbn.notification
        val identity = notification.shortcutId
            ?: notification.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: fallbackTitle?.toString()
            ?: sbn.key
        return "${sbn.packageName}\u0000$identity"
    }

    private fun appLabel(context: Context, packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun isLocked(context: Context): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguardLocked = runCatching { keyguard?.isKeyguardLocked }.getOrNull() ?: false
        val interactive = runCatching { power?.isInteractive }.getOrNull() ?: true
        // Screen off counts as locked: on HyperOS the keyguard flag is not reliably set while the
        // always-on display is showing, which is exactly when announcements are wanted.
        return keyguardLocked || !interactive
    }
}
