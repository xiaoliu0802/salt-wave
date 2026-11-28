package com.saltwave.app

data class LyricLine(val timeMs: Long, val text: String)

/** 解析 LRC，兼容 [mm:ss]、[mm:ss.x]、[mm:ss.xx]、[mm:ss.xxx]。 */
fun parseLrc(raw: String): List<LyricLine> {
    val tag = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    val out = ArrayList<LyricLine>()
    raw.lineSequence().forEach { line ->
        val matches = tag.findAll(line).toList()
        if (matches.isEmpty()) return@forEach
        val text = line.replace(tag, "").trim()
        if (text.isEmpty()) return@forEach
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
            out.add(LyricLine(min * 60_000L + sec * 1_000L + fracMs, text))
        }
    }
    return out.distinctBy { it.timeMs }.sortedBy { it.timeMs }
}

object LyricBook {

    private val cache = HashMap<String, List<LyricLine>>()

    suspend fun load(api: Subsonic?, id: String, artist: String, title: String): List<LyricLine> {
        if (api == null || id.isEmpty() || id.startsWith("demo")) return emptyList()
        cache[id]?.let { return it }
        val lines = runCatching { api.lyrics(id, artist, title) }.getOrDefault(emptyList())
        cache[id] = lines
        return lines
    }

    fun clear() = cache.clear()
}
