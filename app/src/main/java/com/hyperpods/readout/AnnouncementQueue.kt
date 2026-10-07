package com.hyperpods.readout

import com.hyperpods.core.AnnouncementMode

/** One notification, ready to be spoken. */
data class PendingAnnouncement(
    val conversationKey: String,
    val content: String,
    val count: Int = 1,
)

/**
 * Holds announcements that arrived while something else was being spoken.
 *
 * The three [AnnouncementMode]s are three different answers to "a burst of messages arrived":
 * merge them into a count plus the latest one, read every single one, or only keep the newest.
 */
class AnnouncementQueue(private val maxSize: Int) {

    private val ordered = ArrayDeque<PendingAnnouncement>()

    fun offer(announcement: PendingAnnouncement, mode: AnnouncementMode) {
        when (mode) {
            AnnouncementMode.Merge -> {
                val index = ordered.indexOfFirst { it.conversationKey == announcement.conversationKey }
                if (index >= 0) {
                    val existing = ordered[index]
                    // The ones already waiting plus this one; the newest text wins.
                    ordered[index] = announcement.copy(count = existing.count + 1)
                    return
                }
                append(announcement)
            }

            AnnouncementMode.All -> append(announcement)

        }
    }

    private fun append(announcement: PendingAnnouncement) {
        while (ordered.size >= maxSize) {
            ordered.removeFirst()
        }
        ordered.addLast(announcement)
    }

    fun poll(): PendingAnnouncement? = ordered.removeFirstOrNull()

    fun clear() = ordered.clear()

    fun size(): Int = ordered.size

    /** The sentence to speak for [announcement] under [mode]. */
    fun spokenText(
        announcement: PendingAnnouncement,
        mode: AnnouncementMode,
    ): String =
        if (mode == AnnouncementMode.Merge && announcement.count > 1) {
            "还有 ${announcement.count} 条新消息，最后一条：${announcement.content}"
        } else {
            announcement.content
        }
}
