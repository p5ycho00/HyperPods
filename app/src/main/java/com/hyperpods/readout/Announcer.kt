package com.hyperpods.readout

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.hyperpods.core.AnnouncementMode
import com.hyperpods.core.EventLog
import com.hyperpods.core.SettingsStore

/**
 * Serialises announcements.
 *
 * Speech is asynchronous, so anything that arrives while an utterance is playing is held here.
 * How it is held depends on the user's [AnnouncementMode]:
 *  - `Merge` (default): count per conversation and read the latest, so nothing is silently lost
 *    and the readout does not fall behind reality.
 *  - `All`: read every message in order.
 */
object Announcer {

    /** Only used to collapse a burst before the first utterance; `All` skips the wait entirely. */
    private const val SETTLE_MS = 1_200L
    private const val DEDUPE_WINDOW_MS = 30_000L
    private const val MAX_RECENT = 64
    private const val MAX_PENDING = 20
    private const val MAX_SPEAK_RETRIES = 4

    private val handler = Handler(Looper.getMainLooper())
    private val pending = AnnouncementQueue(MAX_PENDING)
    private val recent = LinkedHashMap<String, Long>()

    private var speaking = false
    private var flushScheduled = false
    private var consecutiveFailures = 0
    private var lastContext: Context? = null

    private val flush = Runnable {
        flushScheduled = false
        val next = pending.poll() ?: return@Runnable
        speakNow(next)
    }

    fun offer(context: Context, announcement: PendingAnnouncement, dedupeKey: String) {
        val appContext = context.applicationContext
        lastContext = appContext
        val mode = SettingsStore.state.value.announcementMode

        if (isDuplicate(dedupeKey, announcement.content)) {
            EventLog.info("跳过", "重复内容：${announcement.content}")
            return
        }

        if (!TtsSpeaker.isReady || speaking || flushScheduled) {
            pending.offer(announcement, mode)
            if (speaking || flushScheduled) return
            scheduleFlush(mode)
            return
        }
        speakNow(announcement, appContext)
    }

    private fun speakNow(announcement: PendingAnnouncement, context: Context? = null) {
        val appContext = context ?: lastContext ?: return
        val settings = SettingsStore.state.value
        val mode = settings.announcementMode
        val text = pending.spokenText(announcement, mode)
        speaking = true
        val started = TtsSpeaker.speak(appContext, text) {
            speaking = false
            consecutiveFailures = 0
            if (pending.size() > 0) {
                scheduleFlush(SettingsStore.state.value.announcementMode)
            } else {
                TtsSpeaker.applyTuning()
            }
        }
        if (!started) {
            // The engine is still warming up: hold the text and retry, but never spin forever.
            speaking = false
            consecutiveFailures++
            if (consecutiveFailures > MAX_SPEAK_RETRIES) {
                EventLog.warn("播报", "语音引擎始终未就绪，丢弃待播报内容")
                pending.clear()
                consecutiveFailures = 0
                return
            }
            pending.offer(announcement, mode)
            scheduleFlush(mode)
        }
    }

    private fun scheduleFlush(mode: AnnouncementMode) {
        if (pending.size() == 0 || flushScheduled) return
        flushScheduled = true
        handler.removeCallbacks(flush)
        val delay = if (mode == AnnouncementMode.All) 0L else SETTLE_MS
        handler.postDelayed(flush, delay)
    }

    private fun isDuplicate(dedupeKey: String, content: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        recent.entries.removeAll { now - it.value > DEDUPE_WINDOW_MS }
        val signature = "$dedupeKey\u0000$content"
        if (recent.containsKey(signature)) return true
        recent[signature] = now
        while (recent.size > MAX_RECENT) {
            recent.remove(recent.keys.first())
        }
        return false
    }

    fun stop() {
        handler.removeCallbacks(flush)
        flushScheduled = false
        speaking = false
        consecutiveFailures = 0
        pending.clear()
        TtsSpeaker.stop()
    }
}
