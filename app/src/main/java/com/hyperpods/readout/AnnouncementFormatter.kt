package com.hyperpods.readout

import java.util.regex.Pattern

/**
 * Turns a notification into one spoken sentence.
 *
 * Bundled notifications are the awkward case: their text is a blob such as
 * "[3条]张三: 你好" or a multi-line history, count markers included, and the sender is repeated
 * even though it is already the title. Those artefacts are stripped here so the result reads like
 * a person talking rather than like a notification dump.
 */
object AnnouncementFormatter {

    private const val MAX_CONTENT_LENGTH = 300

    private val whitespace = Regex("\\s+")

    /** "[3条]" / "（3条）" / "【3条】" — optionally followed by a separator. */
    private val bracketedCount =
        Regex("^\\s*[\\[（(【]\\s*\\d+\\s*条[^\\]）)】]{0,6}[\\]）)】]\\s*[:：]?\\s*")

    /** Bare "3条" / "3条新消息：" without brackets. */
    private val bareCount = Regex("^\\s*\\d+\\s*条(新消息|消息|条)?\\s*[:：]?\\s*")

    /** A line that carries nothing but a count, e.g. "[3条新消息]". */
    private val countOnly = Regex("^[\\[（(【]?\\s*\\d+\\s*条(新消息|消息|条)?\\s*[\\]）)】]?$")

    fun format(appName: CharSequence?, title: CharSequence?, text: CharSequence?): String? {
        val cleanAppName = appName.clean() ?: return null
        val cleanTitle = title.bestLine()
        val cleanText = text.bestLine()?.let { withoutSender(it, cleanTitle) }

        val content = when {
            cleanText == null -> cleanTitle
            cleanTitle == null -> cleanText
            cleanTitle.equals(cleanAppName, ignoreCase = true) -> cleanText
            cleanTitle.equals(cleanText, ignoreCase = true) -> cleanText
            else -> "$cleanTitle：$cleanText"
        }?.take(MAX_CONTENT_LENGTH)?.trim() ?: return null

        return content
    }

    /**
     * Picks the newest meaningful line: a bundled notification lists older messages first, and the
     * lines that only contain a count are noise.
     */
    private fun CharSequence?.bestLine(): String? {
        val lines = this?.toString()
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: return null
        val meaningful = lines.filterNot { countOnly.matches(it) }
        val chosen = meaningful.lastOrNull() ?: return null
        return chosen.stripCount().clean()?.takeIf { it.isNotEmpty() }
    }

    /** "[3条]张三: 你好" -> "张三: 你好" */
    private fun String.stripCount(): String =
        bracketedCount.replace(this, "").let { bareCount.replace(it, "") }

    /** "张三: 你好" with title "张三" -> "你好" */
    private fun withoutSender(text: String, sender: String?): String {
        if (sender.isNullOrBlank()) return text
        val pattern = Pattern.compile(
            "^" + Pattern.quote(sender) + "\\s*[:：]\\s*",
            Pattern.CASE_INSENSITIVE,
        )
        val matcher = pattern.matcher(text)
        return if (matcher.find()) text.substring(matcher.end()).trim() else text
    }

    private fun CharSequence?.clean(): String? = this
        ?.toString()
        ?.replace(whitespace, " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
