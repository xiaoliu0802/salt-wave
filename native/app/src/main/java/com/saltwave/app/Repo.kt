package com.saltwave.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** 全局曲库状态（内存缓存，不做离线存储）。 */
object Repo {

    var api: Subsonic? = null
    var demo by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var loaded by mutableStateOf(false)

    val albums = mutableStateListOf<Album>()
    val playlists = mutableStateListOf<Playlist>()
    val starred = mutableStateListOf<Album>()

    /** 全部歌曲：按专辑顺序逐批拉取后拼起来。 */
    val songs = mutableStateListOf<Track>()
    var songsTotal by mutableIntStateOf(0)
    var songsDone by mutableStateOf(false)
    var songsLoading by mutableStateOf(false)
    private var songsCursor = 0

    private val songCache = HashMap<String, List<Track>>()

    private var albumOffset = 0
    private var albumsDone = false
    private var loadingMore = false

    fun reset() {
        albums.clear()
        playlists.clear()
        starred.clear()
        songCache.clear()
        songs.clear()
        songsTotal = 0
        songsDone = false
        songsLoading = false
        songsCursor = 0
        albumOffset = 0
        albumsDone = false
        loadingMore = false
        loaded = false
        loading = false
    }

    /** 一次拉一批专辑里的歌，边拉边显示，不阻塞首页其它 tab。 */
    suspend fun loadAllSongs() {
        val client = api ?: return
        if (songsLoading || songsDone || demo) return
        songsLoading = true
        try {
            if (albums.isEmpty()) load()
            val list = albums.toList()
            songsTotal = list.size
            var cursor = songsCursor
            while (cursor < list.size) {
                val batch = list.subList(cursor, minOf(cursor + BATCH, list.size))
                val fetched = coroutineScope {
                    batch.map { al ->
                        async(Dispatchers.IO) {
                            runCatching { client.albumSongs(al.id) }.getOrDefault(emptyList())
                        }
                    }.awaitAll()
                }
                fetched.forEach { songs.addAll(it) }
                cursor += batch.size
                songsCursor = cursor
            }
            songsDone = true
        } catch (e: Exception) {
            songsDone = true
        } finally {
            songsLoading = false
        }
    }

    suspend fun load() {
        val client = api ?: return
        if (loading) return
        loading = true
        try {
            val a = client.albums(PAGE)
            val p = client.playlists()
            val s = client.starred()
            albums.clear()
            albums.addAll(a)
            albumOffset = a.size
            albumsDone = a.size < PAGE
            playlists.clear()
            playlists.addAll(p)
            starred.clear()
            starred.addAll(s)
            loaded = true
        } finally {
            loading = false
        }
    }

    /** 滚到底自动续页，一次 100 张专辑。 */
    suspend fun loadMoreAlbums() {
        val client = api ?: return
        if (loadingMore || albumsDone || demo) return
        loadingMore = true
        try {
            val more = client.albums(PAGE, albumOffset)
            if (more.isNotEmpty()) {
                albums.addAll(more)
                albumOffset += more.size
            }
            if (more.size < PAGE) albumsDone = true
        } catch (e: Exception) {
            albumsDone = true
        } finally {
            loadingMore = false
        }
    }

    private const val PAGE = 100
    private const val BATCH = 8

    fun cachedSongs(playlist: Boolean, id: String): List<Track>? = songCache[key(playlist, id)]

    suspend fun songs(playlist: Boolean, id: String): List<Track> {
        val k = key(playlist, id)
        songCache[k]?.let { return it }
        val client = api ?: return emptyList()
        val list = if (playlist) client.playlistSongs(id) else client.albumSongs(id)
        songCache[k] = list
        return list
    }

    fun streamUrl(track: Track): String? = api?.streamUrl(track)

    fun coverUrl(coverId: String?): String? = api?.coverUrl(coverId, 480)

    private fun key(playlist: Boolean, id: String) = (if (playlist) "p:" else "a:") + id

    // ---------------- 演示数据 ----------------

    fun fillDemo() {
        reset()
        api = null
        demo = true
        loaded = true
        val names = listOf("夜航", "墨", "黄昏以后", "潮汐线", "黑点", "雾中来信")
        val artists = listOf("北岸乐队", "林砚", "黄昏以后", "潮汐线", "黑点乐队", "雾中来信")
        val covers = intArrayOf(
            R.drawable.cover1, R.drawable.cover2, R.drawable.cover3,
            R.drawable.cover4, R.drawable.cover5, R.drawable.cover6,
        )
        for (i in 0 until 6) {
            albums.add(
                Album(
                    id = "demo-a$i",
                    name = names[i],
                    artist = artists[i],
                    count = 10 + i,
                    coverId = null,
                    demoRes = covers[i],
                )
            )
        }
        val plNames = listOf("深夜循环", "通勤 30 分钟", "最近常听")
        val plCovers = intArrayOf(covers[1], covers[2], covers[5])
        val plCounts = intArrayOf(18, 12, 9)
        for (i in 0 until 3) {
            playlists.add(
                Playlist(
                    id = "demo-p$i",
                    name = plNames[i],
                    count = plCounts[i],
                    coverId = null,
                    demoRes = plCovers[i],
                )
            )
        }
        starred.add(albums[0])
        starred.add(albums[3])
        songs.clear()
        for (i in 0 until 3) songs.addAll(demoSongs(names[i], artists[i], covers[i]))
        songsDone = true
        songsTotal = albums.size
    }

    fun demoSongs(title: String, artist: String, demoRes: Int?): List<Track> {
        val tracks = listOf("Intro", "城市边缘", "旧磁带", "午夜电台", "回声", "Outro")
        val durs = intArrayOf(186, 241, 205, 268, 233, 178)
        return tracks.mapIndexed { i, t ->
            Track(
                id = "demo-$i",
                title = t,
                artist = artist.ifEmpty { "演示数据" },
                album = title,
                duration = durs[i],
                coverId = null,
                albumId = null,
                demoRes = demoRes,
            )
        }
    }
}
