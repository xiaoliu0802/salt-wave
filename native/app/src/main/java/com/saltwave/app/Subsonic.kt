package com.saltwave.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Subsonic / Navidrome 客户端。
 * 认证：u + t=md5(password+salt) + s=salt。
 */
class Subsonic(server: String, private val user: String, pass: String) {

    private val base: String = normalize(server)
    private val salt = "sw" + System.currentTimeMillis().toString(16)
    private val token = md5(pass + salt)

    init {
        if (base.isEmpty()) throw IOException("服务器地址无效")
    }

    suspend fun ping() {
        io { call("ping") }
    }

    suspend fun albums(limit: Int = 100, offset: Int = 0): List<Album> = io {
        val r = call(
            "getAlbumList2",
            "type" to "alphabeticalByName",
            "size" to limit.toString(),
            "offset" to offset.toString(),
        )
        val arr = r.optJSONObject("albumList2")?.optJSONArray("album") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::toAlbum) }
    }

    suspend fun playlists(): List<Playlist> = io {
        val r = call("getPlaylists")
        val arr = r.optJSONObject("playlists")?.optJSONArray("playlist") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Playlist(
                id = o.optString("id"),
                name = o.optString("name", "未命名歌单"),
                count = o.optInt("songCount"),
                coverId = o.optString("coverArt").takeIf { it.isNotEmpty() } ?: o.optString("id"),
            )
        }
    }

    suspend fun starred(): List<Album> = io {
        val r = call("getStarred2")
        val arr = r.optJSONObject("starred2")?.optJSONArray("album") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::toAlbum) }
    }

    suspend fun artists(): List<Artist> = io {
        val r = call("getArtists")
        val indexes = r.optJSONObject("artists")?.optJSONArray("index") ?: JSONArray()
        val out = ArrayList<Artist>()
        for (i in 0 until indexes.length()) {
            val arr = indexes.optJSONObject(i)?.optJSONArray("artist") ?: continue
            for (j in 0 until arr.length()) {
                val o = arr.optJSONObject(j) ?: continue
                val id = o.optString("id")
                out.add(
                    Artist(
                        id = id,
                        name = o.optString("name", "未命名"),
                        count = o.optInt("albumCount"),
                        coverId = o.optString("coverArt").takeIf { it.isNotEmpty() } ?: id,
                    )
                )
            }
        }
        out
    }

    suspend fun artistAlbums(id: String): List<Album> = io {
        val r = call("getArtist", "id" to id)
        val arr = r.optJSONObject("artist")?.optJSONArray("album") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::toAlbum) }
    }

    suspend fun albumSongs(id: String): List<Track> = io {
        val r = call("getAlbum", "id" to id)
        val arr = r.optJSONObject("album")?.optJSONArray("song") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::toTrack) }
    }

    suspend fun playlistSongs(id: String): List<Track> = io {
        val r = call("getPlaylist", "id" to id)
        val arr = r.optJSONObject("playlist")?.optJSONArray("entry") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::toTrack) }
    }

    /** 先试 OpenSubsonic 的结构化歌词，再退回 getLyrics 的纯文本/LRC。 */
    suspend fun lyrics(id: String, artist: String, title: String): List<LyricLine> = io {
        val structured = runCatching {
            val r = call("getLyricsBySongId", "id" to id)
            val list = r.optJSONObject("lyricsList")?.optJSONArray("structuredLyrics")
            val lines = list?.optJSONObject(0)?.optJSONArray("line")
            if (lines == null || lines.length() == 0) {
                emptyList()
            } else {
                (0 until lines.length()).mapNotNull { i ->
                    val o = lines.optJSONObject(i) ?: return@mapNotNull null
                    val text = o.optString("value").ifEmpty { o.optString("text") }
                    LyricLine(o.optLong("start"), text)
                }.filter { it.text.isNotBlank() }
            }
        }.getOrDefault(emptyList())
        if (structured.isNotEmpty()) return@io structured

        val plain = runCatching {
            call("getLyrics", "artist" to artist, "title" to title)
                .optJSONObject("lyrics")?.optString("value").orEmpty()
        }.getOrDefault("")
        if (plain.isBlank()) {
            emptyList()
        } else {
            parseLrc(plain).ifEmpty { listOf(LyricLine(0L, plain.trim())) }
        }
    }

    fun streamUrl(track: Track): String {
        val extra = ArrayList<Pair<String, String?>>()
        if (AppSettings.bitrate > 0) extra.add("maxBitRate" to AppSettings.bitrate.toString())
        if (AppSettings.forceMp3) extra.add("format" to "mp3")
        return build("stream", "id" to track.id, *extra.toTypedArray())
    }

    fun coverUrl(coverId: String?, size: Int = 480): String? =
        if (coverId.isNullOrEmpty()) null else build("getCoverArt", "id" to coverId, "size" to size.toString())

    // ---------------- 内部 ----------------

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun build(method: String, vararg kv: Pair<String, String?>): String {
        val sb = StringBuilder()
        sb.append(base).append("/rest/").append(method)
            .append("?u=").append(enc(user))
            .append("&t=").append(token)
            .append("&s=").append(salt)
            .append("&v=1.16.1&c=SaltWave&f=json")
        kv.forEach { (k, v) -> if (v != null) sb.append('&').append(k).append('=').append(enc(v)) }
        return sb.toString()
    }

    private fun call(method: String, vararg kv: Pair<String, String?>): JSONObject {
        val conn = URL(build(method, *kv)).openConnection() as HttpURLConnection
        conn.connectTimeout = 9000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "SaltWave/1.3")
        try {
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val root = JSONObject(body)
            val r = root.optJSONObject("subsonic-response")
                ?: throw IOException("这个地址看起来不是 Subsonic 服务")
            if (r.optString("status") != "ok") {
                val err = r.optJSONObject("error")
                val msg = err?.optString("message")?.takeIf { it.isNotEmpty() } ?: "认证失败"
                val codeText = if (err != null) " (${err.optInt("code")})" else ""
                throw IOException(msg + codeText)
            }
            return r
        } finally {
            conn.disconnect()
        }
    }

    private fun toAlbum(o: JSONObject): Album {
        val cover = o.optString("coverArt").takeIf { it.isNotEmpty() }
            ?: o.optString("id").takeIf { it.isNotEmpty() }
        return Album(
            id = o.optString("id"),
            name = o.optString("name", "未命名专辑"),
            artist = o.optString("artist").takeIf { it.isNotEmpty() }
                ?: o.optString("albumArtist"),
            count = o.optInt("songCount"),
            coverId = cover,
        )
    }

    private fun toTrack(o: JSONObject): Track {
        val cover = o.optString("coverArt").takeIf { it.isNotEmpty() }
        return Track(
            id = o.optString("id"),
            title = o.optString("title", "未命名"),
            artist = o.optString("artist"),
            album = o.optString("album"),
            duration = o.optInt("duration"),
            coverId = cover,
            albumId = o.optString("albumId").takeIf { it.isNotEmpty() },
        )
    }

    companion object {
        private fun enc(v: String): String = try {
            URLEncoder.encode(v, "UTF-8")
        } catch (e: Exception) {
            v
        }

        private fun normalize(server: String): String {
            var b = server.trim()
            if (b.isEmpty()) return ""
            if (!b.startsWith("http://") && !b.startsWith("https://")) b = "http://$b"
            while (b.endsWith("/")) b = b.dropLast(1)
            if (b.endsWith("/rest")) b = b.dropLast(5)
            return b
        }

        fun md5(s: String): String {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(bytes.size * 2)
            bytes.forEach { b ->
                val v = b.toInt() and 0xFF
                if (v < 16) sb.append('0')
                sb.append(Integer.toHexString(v))
            }
            return sb.toString()
        }
    }
}
