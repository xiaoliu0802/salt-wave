package com.saltwave.app

data class LyricLine(val timeMs: Long, val text: String)

private val TimeTag = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
private val MetaTag = Regex("\\[(ti|ar|al|by|offset|re|ve|length|#\\w+):[^]]*]", RegexOption.IGNORE_CASE)
private val OffsetTag = Regex("\\[offset:(-?\\d+)]", RegexOption.IGNORE_CASE)
private val WordTag = Regex("<\\d{1,2}:\\d{1,2}(?:[.:]\\d{1,3})?>")
private val JunkTag = Regex("\\[(awlrc|kugou|qqmusic|kgmusic|yylyric)[^]]*]", RegexOption.IGNORE_CASE)

/** 内嵌歌词常见的垃圾块，比如 [awlrc:lrc:BASE64...]、整段 base64、加密头。 */
fun isJunkLyricText(raw: String): Boolean {
    val t = raw.trim()
    if (t.isEmpty()) return true
    if (t.contains("awlrc", true)) return true
    if (t.length >= 60 && !t.contains(' ')) {
        val code = t.count { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        if (code >= t.length * 0.9) return true
    }
    return false
}

/** 去掉增强歌词的逐字标签，保留可显示文本。 */
fun cleanLyricText(raw: String): String =
    WordTag.replace(JunkTag.replace(MetaTag.replace(raw, ""), ""), "").trim()

/** 结构化歌词里整段都是 0 时间戳时，按总时长均摊。 */
fun distributeLyrics(lines: List<LyricLine>, durationMs: Long): List<LyricLine> {
    if (lines.size < 3 || lines.any { it.timeMs > 0L }) return lines
    if (durationMs < 3000) return lines
    val span = durationMs - 1500L
    return lines.mapIndexed { i, l ->
        LyricLine(span * i / (lines.size - 1), l.text)
    }
}

/** 解析 LRC，兼容 [mm:ss]、[mm:ss.x]、[mm:ss.xx]、[mm:ss.xxx] 与 [offset:]。 */
fun parseLrc(raw: String, durationMs: Long = 0L): List<LyricLine> {
    val out = ArrayList<LyricLine>()
    val plain = ArrayList<String>()
    var offset = 0L

    raw.lineSequence().forEach { rawLine ->
        var line = rawLine
        OffsetTag.find(line)?.let { offset = it.groupValues[1].toLongOrNull() ?: 0L }
        line = MetaTag.replace(line, "")
        line = JunkTag.replace(line, "")
        line = WordTag.replace(line, "")
        val matches = TimeTag.findAll(line).toList()
        if (matches.isEmpty()) {
            val t = line.trim()
            if (t.isNotEmpty() && !isJunkLyricText(t)) plain.add(t)
            return@forEach
        }
        val text = cleanLyricText(TimeTag.replace(line, ""))
        if (text.isEmpty() || isJunkLyricText(text)) return@forEach
        matches.forEach { m ->
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toLongOrNull() ?: 0L
            val frac = m.groupValues[3]
            val fracMs = when (frac.length) {
                0 -> 0L
                1 -> (frac.toLongOrNull() ?: 0L) * 100L
                2 -> (frac.toLongOrNull() ?: 0L) * 10L
                else -> frac.take(3).toLongOrNull() ?: 0L
            }
            val at = min * 60_000L + sec * 1_000L + fracMs - offset
            out.add(LyricLine(at.coerceAtLeast(0L), text))
        }
    }

    val timed = out.distinctBy { it.timeMs }.sortedBy { it.timeMs }
    if (timed.isNotEmpty()) return timed
    return distributeLyrics(plain.map { LyricLine(0L, it) }, durationMs)
}

object LyricBook {

    private val cache = HashMap<String, List<LyricLine>>()

    suspend fun load(
        api: Subsonic?,
        id: String,
        artist: String,
        title: String,
        durationMs: Long = 0L,
    ): List<LyricLine> {
        if (api == null || id.isEmpty() || id.startsWith("demo")) return emptyList()
        cache[id]?.let { return it }
        val lines = runCatching { api.lyrics(id, artist, title, durationMs) }.getOrDefault(emptyList())
        if (lines.isNotEmpty()) cache[id] = lines
        return lines
    }

    fun clear() = cache.clear()
}
