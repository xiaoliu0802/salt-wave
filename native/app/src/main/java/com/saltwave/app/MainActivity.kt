@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.saltwave.app

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

sealed interface Screen {
    data class Home(val tab: Int = 0) : Screen
    data class Detail(
        val playlist: Boolean,
        val id: String,
        val name: String,
        val artist: String,
        val coverId: String?,
        val demoRes: Int?,
        val count: Int,
    ) : Screen

    data object Player : Screen
    data class Artist(
        val id: String,
        val name: String,
        val coverId: String?,
        val demoRes: Int?,
    ) : Screen
    data object Settings : Screen
    data object Login : Screen
}

class MainActivity : ComponentActivity() {

    private var controller by mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        restoreLibrary()

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener(
            {
                val c = try {
                    future.get()
                } catch (e: Exception) {
                    null
                }
                controller = c
                if (c != null) Now.bind(c)
            },
            ContextCompat.getMainExecutor(this),
        )

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
        }

        setContent {
            val dark = when (AppSettings.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            SaltTheme(dark = dark, accent = currentAccent()) {
                AppRoot(controller = controller)
            }
        }
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }

    private fun restoreLibrary() {
        if (AppSettings.demo) {
            Repo.fillDemo()
            return
        }
        if (AppSettings.server.isNotEmpty()) {
            Repo.api = Subsonic(AppSettings.server, AppSettings.user, AppSettings.pass)
        }
    }
}

@Composable
fun AppRoot(controller: MediaController?) {
    val context = LocalContext.current
    val c = sw()
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home(0))) }
    var queueOpen by remember { mutableStateOf(false) }
    var miniLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    val current = stack.last()
    val dark = when (AppSettings.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    LaunchedEffect(Now.mediaId) {
        val id = Now.mediaId
        miniLines = if (id == null) emptyList() else LyricBook.load(Repo.api, id, Now.artist, Now.title)
    }

    val miniLine = if (miniLines.isEmpty()) {
        ""
    } else {
        val idx = miniLines.indexOfLast { it.timeMs <= Now.position + AppSettings.lyricOffsetMs }
        if (idx >= 0) miniLines[idx].text else ""
    }

    LaunchedEffect(controller) {
        while (true) {
            delay(520)
            val p = controller ?: continue
            val target = Now.seekTarget
            if (target != null) {
                // 跳转后给播放器 1.2 秒去真的走位，期间不信它报旧位置
                if (System.currentTimeMillis() - Now.seekAt > 1200) {
                    Now.seekTarget = null
                } else {
                    continue
                }
            }
            Now.position = p.currentPosition
            if (p.duration > 0) Now.duration = p.duration
            Now.playing = p.isPlaying
            Now.buffering = p.playbackState == Player.STATE_BUFFERING
        }
    }

    LaunchedEffect(Now.sleepUntil) {
        val until = Now.sleepUntil ?: return@LaunchedEffect
        while (true) {
            delay(1000)
            if (System.currentTimeMillis() >= until) {
                controller?.pause()
                Now.sleepUntil = null
                break
            }
        }
    }

    LaunchedEffect(current) {
        if (current is Screen.Home && Repo.api != null && !Repo.loaded && !Repo.loading) {
            runCatching { Repo.load() }
        }
    }

    // 曲库一就绪就在后台把全部歌曲拉完，不用等用户点“歌曲”tab
    LaunchedEffect(Repo.loaded, Repo.demo) {
        if (Repo.loaded && Repo.api != null && !Repo.demo && !Repo.songsDone) {
            runCatching { Repo.loadAllSongs() }
        }
    }

    BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }

    val playerOpen = current is Screen.Player
    SideEffect {
        val window = (context as Activity).window
        val bg = if (playerOpen || dark) 0xFF0B0E14.toInt() else 0xFFF5F6F8.toInt()
        window.statusBarColor = bg
        window.navigationBarColor = bg
        var flags = 0
        if (!playerOpen && !dark) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (playerOpen) c.playerBg else c.page)
    ) {
        when (current) {
            is Screen.Home -> HomeScreen(
                tab = current.tab,
                controller = controller,
                onTab = { t -> stack = stack.dropLast(1) + Screen.Home(t) },
                onServer = { stack = stack + Screen.Login },
                onSettings = { stack = stack + Screen.Settings },
                onAlbum = { a ->
                    stack = stack + Screen.Detail(false, a.id, a.name, a.artist, a.coverId, a.demoRes, a.count)
                },
                onPlaylist = { p ->
                    stack = stack + Screen.Detail(true, p.id, p.name, "", p.coverId, p.demoRes, p.count)
                },
                onArtist = { a ->
                    stack = stack + Screen.Artist(a.id, a.name, a.coverId, a.demoRes)
                },
            )

            is Screen.Artist -> ArtistScreen(
                screen = current,
                controller = controller,
                onBack = { stack = stack.dropLast(1) },
                onAlbum = { al ->
                    stack = stack + Screen.Detail(false, al.id, al.name, al.artist, al.coverId, al.demoRes, al.count)
                },
            )

            is Screen.Login -> LoginScreen(
                onDone = { stack = listOf(Screen.Home(0)) },
                onBack = { stack = stack.dropLast(1) },
            )

            is Screen.Detail -> DetailScreen(
                screen = current,
                controller = controller,
                onBack = { stack = stack.dropLast(1) },
            )

            Screen.Player -> PlayerScreen(
                controller = controller,
                onBack = { stack = stack.dropLast(1) },
                onSettings = { stack = stack + Screen.Settings },
                onQueue = { queueOpen = true },
            )

            Screen.Settings -> SettingsScreen(
                controller = controller,
                onBack = { stack = stack.dropLast(1) },
                onLogin = { stack = stack + Screen.Login },
            )
        }

        if (!playerOpen && AppSettings.showMiniPlayer && Now.title.isNotEmpty() && controller != null) {
            MiniPlayer(
                controller = controller,
                lyric = miniLine,
                modifier = Modifier.align(Alignment.BottomCenter),
                onClick = { stack = stack + Screen.Player },
                onQueue = { queueOpen = true },
            )
        }
    }

    if (queueOpen) {
        QueueSheet(controller = controller, onClose = { queueOpen = false })
    }
}

// ---------------------------------------------------------------- 首页

@Composable
fun HomeScreen(
    tab: Int,
    controller: MediaController?,
    onTab: (Int) -> Unit,
    onServer: () -> Unit,
    onSettings: () -> Unit,
    onAlbum: (Album) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onArtist: (Artist) -> Unit,
) {
    val c = sw()
    val context = LocalContext.current

    LaunchedEffect(tab) {
        if (tab == 0 && Repo.api != null && !Repo.demo && !Repo.songsDone) {
            runCatching { Repo.loadAllSongs() }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Salt Wave",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = c.ink,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = c.sub, modifier = Modifier.size(20.dp))
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (Repo.api != null) c.accentSoft else c.field,
                onClick = onServer,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (Repo.api != null) c.accent else c.sub))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        when {
                            Repo.demo -> "演示"
                            Repo.api != null -> "已连接"
                            else -> "未连接"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (Repo.api != null) c.accent else c.sub,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            val names = listOf("歌曲", "专辑", "艺术家", "歌单", "收藏")
            names.forEachIndexed { i, n ->
                val on = tab == i
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (on) c.accent else c.field,
                    onClick = { onTab(i) },
                    modifier = Modifier.padding(end = 6.dp),
                ) {
                    Text(
                        n,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (on) Color.White else c.sub,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        when {
            Repo.api == null && !Repo.demo -> EmptyBox("还没有连接服务器\n点右上角“服务器”填入地址后就能看到你的曲库")
            Repo.loading -> EmptyBox("正在从服务器读取…")
            tab == 0 -> {
                val list = Repo.songs.toList()
                val scope = rememberCoroutineScope()
                when {
                    list.isEmpty() && Repo.songsLoading ->
                        EmptyBox("正在读取歌曲…\n先把专辑列表翻完，再一张张取歌")

                    list.isEmpty() && Repo.songsDone -> EmptyBox("曲库里还没有歌")

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (AppSettings.showMiniPlayer) 96.dp else 20.dp),
                    ) {
                        item {
                            val text = when {
                                Repo.songsLoading ->
                                    "读取中 · ${Repo.songs.size} 首" +
                                        if (Repo.songsExpected > 0) " / 共 ${Repo.songsExpected} 首" else ""

                                Repo.songsFailed > 0 ->
                                    "已读 ${Repo.songs.size} 首 · ${Repo.songsFailed} 张专辑没收全，点此重试"

                                else -> "共 ${Repo.songs.size} 首 · ${Repo.songsTotal} 张专辑"
                            }
                            Text(
                                text,
                                fontSize = 11.sp,
                                color = if (Repo.songsFailed > 0) c.accent else c.sub,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !Repo.songsLoading && Repo.songsFailed > 0) {
                                        Repo.retrySongs()
                                        scope.launch { runCatching { Repo.loadAllSongs() } }
                                    }
                                    .padding(bottom = 6.dp),
                            )
                        }
                        itemsIndexed(list) { index, track ->
                            TrackRow(track, Now.mediaId == track.id) {
                                startPlay(controller, list, index, context)
                            }
                        }
                    }
                }
            }

            tab == 2 -> {
                val list = Repo.artists.toList()
                if (list.isEmpty()) {
                    EmptyBox("还没有艺术家信息\n连上服务器后会自动读取")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (AppSettings.showMiniPlayer) 96.dp else 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(list.size) { index ->
                            val ar = list[index]
                            ArtistCard(ar) { onArtist(ar) }
                        }
                    }
                }
            }

            else -> {
                val items: List<Any> = when (tab) {
                    1 -> Repo.albums.toList()
                    3 -> Repo.playlists.toList()
                    else -> Repo.starred.toList()
                }
                if (items.isEmpty()) {
                    EmptyBox(if (tab == 4) "还没有收藏的专辑" else "这里是空的")
                } else {
                    val gridState = rememberLazyGridState()
                    val lastVisible = remember {
                        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                    }
                    LaunchedEffect(tab) {
                        while (true) {
                            delay(400)
                            if (tab == 1 && Repo.api != null && !Repo.demo &&
                                lastVisible.value >= Repo.albums.size - 6
                            ) {
                                runCatching { Repo.loadMoreAlbums() }
                            }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(AppSettings.gridColumns.coerceIn(2, 3)),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (AppSettings.showMiniPlayer) 96.dp else 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(items.size) { index ->
                            when (val item = items[index]) {
                                is Album -> AlbumCard(item, onAlbum)
                                is Playlist -> PlaylistCard(item, onPlaylist)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumCard(album: Album, onClick: (Album) -> Unit) {
    val c = sw()
    Column(Modifier.clickable { onClick(album) }) {
        Box {
            CoverImage(album.coverId, album.demoRes, Modifier.fillMaxWidth().aspectRatio(1f))
            Badge("专辑", Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(
            album.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (album.artist.isEmpty()) "${album.count} 首" else "${album.artist} · ${album.count} 首",
            fontSize = 11.sp,
            color = c.sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: (Playlist) -> Unit) {
    val c = sw()
    Column(Modifier.clickable { onClick(playlist) }) {
        Box {
            CoverImage(playlist.coverId, playlist.demoRes, Modifier.fillMaxWidth().aspectRatio(1f))
            Badge("歌单", Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(
            playlist.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text("${playlist.count} 首", fontSize = 11.sp, color = c.sub, maxLines = 1)
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(Color(0x66000000), RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color.White,
        fontSize = 10.sp,
    )
}

@Composable
fun CoverImage(coverId: String?, demoRes: Int?, modifier: Modifier = Modifier, radius: Dp? = null) {
    val c = sw()
    val r = radius ?: AppSettings.coverRadius.dp
    val base = modifier.clip(RoundedCornerShape(r)).background(c.placeholder)
    when {
        demoRes != null -> Image(
            painter = painterResource(demoRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base,
        )

        coverId != null -> AsyncImage(
            model = Repo.coverUrl(coverId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base,
        )

        else -> Box(base)
    }
}

@Composable
fun ArtistCard(a: Artist, onClick: () -> Unit) {
    val c = sw()
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverImage(a.coverId, a.demoRes, Modifier.fillMaxWidth().aspectRatio(1f), radius = 200.dp)
        Spacer(Modifier.height(9.dp))
        Text(
            a.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text("${a.count} 张专辑", fontSize = 11.sp, color = c.sub, maxLines = 1)
    }
}

// ---------------------------------------------------------------- 详情

@Composable
fun DetailScreen(screen: Screen.Detail, controller: MediaController?, onBack: () -> Unit) {
    val c = sw()
    val context = LocalContext.current
    var songs by remember(screen.id) { mutableStateOf(Repo.cachedSongs(screen.playlist, screen.id)) }
    var error by remember(screen.id) { mutableStateOf<String?>(null) }
    var loading by remember(screen.id) { mutableStateOf(false) }

    LaunchedEffect(screen.id) {
        if (Repo.demo) {
            songs = Repo.demoSongs(screen.name, screen.artist, screen.demoRes)
        } else if (songs == null) {
            loading = true
            try {
                songs = Repo.songs(screen.playlist, screen.id)
            } catch (e: Exception) {
                error = e.message
            }
            loading = false
        }
    }

    val list = songs

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(34.dp, 40.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "返回", tint = c.ink, modifier = Modifier.size(18.dp))
            }
            Text(
                screen.name,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(34.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(screen.coverId, screen.demoRes, Modifier.size(80.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    screen.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (screen.artist.isNotEmpty()) {
                    Text(
                        screen.artist,
                        fontSize = 12.sp,
                        color = c.sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    "${list?.size ?: screen.count} 首 · ${AppSettings.bitrateText()}",
                    fontSize = 11.sp,
                    color = c.sub,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (list.isNullOrEmpty()) {
                        toast(context, "曲目还没加载出来")
                    } else {
                        startPlay(controller, list, 0, context)
                    }
                },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("播放全部", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = c.field,
                onClick = {
                    if (list.isNullOrEmpty()) {
                        toast(context, "曲目还没加载出来")
                    } else {
                        startPlay(controller, list, 0, context, shuffle = true)
                    }
                },
                modifier = Modifier.height(44.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = c.ink, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("随机", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        when {
            list == null && loading -> EmptyBox("正在加载曲目…")
            list == null && error != null -> EmptyBox("读取曲目失败：$error")
            list.isNullOrEmpty() -> EmptyBox("这个列表里没有歌曲")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (AppSettings.showMiniPlayer) 96.dp else 20.dp),
            ) {
                items(list) { track ->
                    val index = list.indexOf(track)
                    TrackRow(
                        track = track,
                        playing = Now.mediaId == track.id,
                        onClick = { startPlay(controller, list, index, context) },
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(
    screen: Screen.Artist,
    controller: MediaController?,
    onBack: () -> Unit,
    onAlbum: (Album) -> Unit,
) {
    val c = sw()
    var albums by remember(screen.id) { mutableStateOf(Repo.cachedArtistAlbums(screen.id)) }
    var loading by remember(screen.id) { mutableStateOf(false) }

    LaunchedEffect(screen.id) {
        if (Repo.demo) {
            albums = Repo.albums.filter { it.artist == screen.name }
        } else {
            loading = true
            albums = Repo.artistAlbums(screen.id)
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Rounded.ArrowBackIosNew,
                    contentDescription = "返回",
                    tint = c.ink,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    screen.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${albums?.size ?: 0} 张专辑",
                    fontSize = 11.sp,
                    color = c.sub,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val data = albums
        when {
            loading && data.isNullOrEmpty() -> EmptyBox("正在读取专辑…")
            data.isNullOrEmpty() -> EmptyBox("这位艺术家还没有专辑")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(AppSettings.gridColumns.coerceIn(2, 3)),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(data.size) { index -> AlbumCard(data[index], onAlbum) }
            }
        }
    }
}

@Composable
fun TrackRow(track: Track, playing: Boolean, onClick: () -> Unit) {
    val c = sw()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            CoverImage(track.coverId, track.demoRes, Modifier.size(44.dp), 8.dp)
            if (playing) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x55000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (playing) c.accent else c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                fontSize = 11.sp,
                color = c.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(mmss(track.duration * 1000L), fontSize = 11.sp, color = c.sub)
    }
}

// ---------------------------------------------------------------- 播放页

@Composable
fun LegacyPlayerScreen(
    controller: MediaController?,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onQueue: () -> Unit,
) {
    val c = sw()
    val context = LocalContext.current
    var showSleep by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var lyricLines by remember(Now.mediaId) { mutableStateOf<List<LyricLine>>(emptyList()) }
    var lyricLoading by remember(Now.mediaId) { mutableStateOf(false) }

    val duration = Now.duration
    val position = if (dragging) dragValue.toLong() else Now.position
    val rp = c.playerInk
    val rsub = c.playerSub

    LaunchedEffect(Now.mediaId) {
        val id = Now.mediaId ?: return@LaunchedEffect
        lyricLines = emptyList()
        lyricLoading = true
        lyricLines = LyricBook.load(Repo.api, id, Now.artist, Now.title)
        lyricLoading = false
    }

    Column(Modifier.fillMaxSize().background(c.playerBg).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "返回", tint = rp, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("正在播放", fontSize = 11.sp, color = rsub)
                Text(
                    Now.album.ifEmpty { "Salt Wave" },
                    fontSize = 12.sp,
                    color = rp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onQueue, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = "播放队列", tint = rp, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (Now.artwork != null) {
                AsyncImage(
                    model = Now.artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.60f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF161A22)),
                )
            } else {
                Box(
                    Modifier.fillMaxWidth(0.60f).aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp)).background(Color(0xFF161A22)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color(0xFF39404E), modifier = Modifier.size(48.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            Now.title.ifEmpty { "还没有正在播放的歌" },
            modifier = Modifier.fillMaxWidth(),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = rp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            Now.artist,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            fontSize = 12.sp,
            color = rsub,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(mmss(position), fontSize = 10.sp, color = rsub, modifier = Modifier.weight(1f))
            Text("-" + mmss((duration - position).coerceAtLeast(0)), fontSize = 10.sp, color = rsub)
        }
        Slider(
            value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
            valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                val target = dragValue.toLong()
                controller?.seekTo(target)
                Now.markSeek(target)
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = rp,
                activeTrackColor = c.accent,
                inactiveTrackColor = Color(0xFF2A303C),
            ),
            modifier = Modifier.fillMaxWidth().height(30.dp),
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shuffleOn = Now.shuffle
            IconButton(onClick = {
                controller?.shuffleModeEnabled = !shuffleOn
            }) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "随机",
                    tint = if (shuffleOn) c.accent else Color(0xFF7E8798),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = { controller?.seekToPreviousMediaItem() }, modifier = Modifier.size(46.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首", tint = rp, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(10.dp))
            FilledIconButton(
                onClick = {
                    val p = controller ?: return@FilledIconButton
                    when {
                        p.isPlaying -> p.pause()
                        p.playbackState == Player.STATE_ENDED -> {
                            p.seekTo(0)
                            p.play()
                        }

                        else -> p.play()
                    }
                },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.accent),
            ) {
                if (Now.buffering) {
                    CircProgress()
                } else {
                    Icon(
                        if (Now.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = { controller?.seekToNextMediaItem() }, modifier = Modifier.size(46.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", tint = rp, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = {
                controller?.repeatMode = when (Now.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }) {
                Icon(
                    if (Now.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = "循环",
                    tint = if (Now.repeatMode == Player.REPEAT_MODE_OFF) Color(0xFF7E8798) else c.accent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DarkPill("音质 ${AppSettings.bitrateText()}") { onSettings() }
            DarkPill("${formatSpeed(Now.speed)}×") {
                val next = SpeedOptions[(SpeedOptions.indexOfFirst { it == Now.speed }.let { if (it < 0) 1 else it } + 1) % SpeedOptions.size]
                AppSettings.speed = next
                AppSettings.persist()
                controller?.setPlaybackSpeed(next)
            }
            DarkPill("-${AppSettings.seekStep}s") {
                val t = (Now.position - AppSettings.seekStep * 1000L).coerceAtLeast(0)
                controller?.seekTo(t)
                Now.markSeek(t)
            }
            DarkPill("+${AppSettings.seekStep}s") {
                val t = (Now.position + AppSettings.seekStep * 1000L).let { if (duration > 0) it.coerceAtMost(duration) else it }
                controller?.seekTo(t)
                Now.markSeek(t)
            }
            DarkPill(
                text = if (Now.sleepUntil != null) "定时 ${sleepLeftText()}" else "定时",
                active = Now.sleepUntil != null,
            ) { showSleep = true }
            DarkPill("歌词 ${if (AppSettings.showLyricPanel) "开" else "关"}", active = AppSettings.showLyricPanel) {
                AppSettings.showLyricPanel = !AppSettings.showLyricPanel
                AppSettings.persist()
            }
        }

        if (AppSettings.showLyricPanel) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (lyricLines.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (lyricLoading) "正在取歌词…" else "这首歌没有歌词",
                            fontSize = 12.sp,
                            color = rsub,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    val offset = AppSettings.lyricOffsetMs
                    val active = lyricLines.indexOfLast { it.timeMs <= position + offset }
                    val ls = rememberLazyListState()
                    LaunchedEffect(active) {
                        if (active >= 1) ls.animateScrollToItem(active - 1)
                    }
                    LazyColumn(
                        state = ls,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 18.dp),
                    ) {
                        items(lyricLines) { line ->
                            val idx = lyricLines.indexOf(line)
                            val on = idx == active
                            Text(
                                line.text,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val t = (line.timeMs - offset).coerceAtLeast(0)
                                        controller?.seekTo(t)
                                        Now.markSeek(t)
                                    }
                                    .padding(vertical = 7.dp),
                                fontSize = (if (on) AppSettings.lyricSize + 3 else AppSettings.lyricSize).sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = if (on) rp else rsub.copy(alpha = 0.62f),
                                textAlign = if (AppSettings.lyricCenter) TextAlign.Center else TextAlign.Start,
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("点上面“歌词 开”显示逐行歌词", fontSize = 12.sp, color = rsub.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showSleep) {
        val minutes = listOf(15, 30, 45, 60)
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("睡眠定时") },
            text = {
                Column {
                    Text("到点自动暂停播放。", fontSize = 12.sp, color = c.sub)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        minutes.forEach { m ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = c.accentSoft,
                                onClick = {
                                    Now.sleepUntil = System.currentTimeMillis() + m * 60_000L
                                    showSleep = false
                                },
                            ) {
                                Text(
                                    "${m}分",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = c.accent,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { Now.sleepUntil = null; showSleep = false }) {
                    Text("关闭定时")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSleep = false }) { Text("取消") }
            },
            containerColor = c.card,
            titleContentColor = c.ink,
            textContentColor = c.ink,
        )
    }
}

private fun sleepLeftText(): String {
    val until = Now.sleepUntil ?: return ""
    val left = ((until - System.currentTimeMillis()) / 60000L).coerceAtLeast(0)
    return "${left}分"
}

@Composable
fun DarkPill(text: String, active: Boolean = false, onClick: () -> Unit) {
    val c = sw()
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = if (active) c.accent.copy(alpha = 0.22f) else Color(0x14FFFFFF),
        onClick = onClick,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) c.accent else c.playerSub,
        )
    }
}

@Composable
fun MiniPlayer(
    controller: MediaController,
    lyric: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onQueue: () -> Unit,
) {
    val c = sw()
    val dur = Now.duration
    val frac = if (dur > 0) (Now.position.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
    var dragX by remember { mutableFloatStateOf(0f) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Now.mediaId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX < -70f) {
                            controller.seekToNextMediaItem()
                        } else if (dragX > 70f) {
                            controller.seekToPreviousMediaItem()
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                ) { _, dx -> dragX += dx }
            },
        color = c.card,
        shadowElevation = 10.dp,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = c.accent,
                trackColor = c.divider,
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = Now.artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.placeholder),
                    )
                    if (Now.buffering) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x66000000)),
                            contentAlignment = Alignment.Center,
                        ) { CircProgress(16.dp) }
                    }
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        Now.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        lyric.ifEmpty { Now.artist },
                        fontSize = 11.sp,
                        color = if (lyric.isNotEmpty()) lyricColor() else c.sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                IconButton(
                    onClick = { if (Now.playing) controller.pause() else controller.play() },
                    modifier = Modifier.size(40.dp),
                ) {
                    if (Now.buffering) {
                        CircProgress(20.dp, c.accent)
                    } else {
                        Icon(
                            if (Now.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "播放/暂停",
                            tint = c.accent,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                IconButton(onClick = onQueue, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "播放列表", tint = c.sub, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun CircProgress(size: Dp = 26.dp, color: Color = Color.White) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        strokeWidth = 2.dp,
    )
}

@Composable
fun QueueSheet(controller: MediaController?, onClose: () -> Unit) {
    val c = sw()
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF12161E),
        contentColor = Color(0xFFE9EDF5),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "播放队列 ${Now.queue.size} 首",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF3F6FB),
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { controller?.clearMediaItems() }) {
                    Text("清空", fontSize = 12.sp, color = Color(0xFF98A1B2))
                }
            }
            LazyColumn(Modifier.fillMaxWidth().height(400.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(Now.queue) { entry ->
                    val i = Now.queue.indexOf(entry)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                controller?.seekTo(i, 0L)
                                onClose()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (i == Now.index) "▶" else "${i + 1}",
                            fontSize = 11.sp,
                            color = if (i == Now.index) c.accent else Color(0xFF7E8798),
                            modifier = Modifier.width(26.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (i == Now.index) c.accent else Color(0xFFE9EDF5),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                entry.artist,
                                fontSize = 11.sp,
                                color = Color(0xFF98A1B2),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 设置

@Composable
fun SettingsScreen(controller: MediaController?, onBack: () -> Unit, onLogin: () -> Unit) {
    val c = sw()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "返回", tint = c.ink, modifier = Modifier.size(17.dp))
            }
            Text("设置", modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.width(38.dp))
        }
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (AppSettings.showMiniPlayer) 100.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GroupTitle("服务器")
                SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("连接", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                            Text(
                                when {
                                    Repo.demo -> "演示模式（不能播放）"
                                    AppSettings.server.isEmpty() -> "未连接"
                                    else -> AppSettings.server
                                },
                                fontSize = 11.sp,
                                color = c.sub,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        SwChip("修改", true) { onLogin() }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.divider)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("重新读取曲库", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                            Text("清掉当前列表，重新拉一次", fontSize = 11.sp, color = c.sub, modifier = Modifier.padding(top = 3.dp))
                        }
                        IconButton(onClick = {
                            scope.launch {
                                Repo.reset()
                                runCatching { Repo.load() }.onFailure {
                                    toast(context, "读取失败：${it.message ?: "未知错误"}")
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "重新读取", tint = c.accent)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.divider)
                    SwitchRow(
                        title = "演示数据",
                        desc = "只用来预览界面，不能播放",
                        checked = AppSettings.demo,
                        onChange = { on ->
                            if (on) {
                                Repo.fillDemo()
                                AppSettings.demo = true
                                AppSettings.persist()
                            } else {
                                if (AppSettings.server.isEmpty()) {
                                    toast(context, "先连接你的 Navidrome")
                                } else {
                                    Repo.demo = false
                                    AppSettings.demo = false
                                    AppSettings.persist()
                                    Repo.api = Subsonic(AppSettings.server, AppSettings.user, AppSettings.pass)
                                    Repo.reset()
                                }
                            }
                        },
                    )
                }
            }

            item {
                GroupTitle("用户界面")
                SettingCard {
                    Text("深色模式", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("跟随系统", "浅色", "深色").forEachIndexed { i, n ->
                            SwChip(n, AppSettings.themeMode == i) {
                                AppSettings.themeMode = i
                                AppSettings.persist()
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = c.divider)
                    Text("主题色", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AccentPalette.forEachIndexed { i, pair ->
                            val on = AppSettings.accentIndex == i
                            Box(
                                Modifier.size(28.dp)
                                    .clip(CircleShape)
                                    .background(pair.first)
                                    .clickable {
                                        AppSettings.accentIndex = i
                                        AppSettings.persist()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = c.divider)
                    SliderRow(
                        title = "封面圆角",
                        value = "${AppSettings.coverRadius}dp",
                        valueFloat = AppSettings.coverRadius.toFloat(),
                        range = 0f..26f,
                        onChange = {
                            AppSettings.coverRadius = it.toInt()
                            AppSettings.persist()
                        },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = c.divider)
                    Text("网格列数", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3).forEach { n ->
                            SwChip("$n 列", AppSettings.gridColumns == n) {
                                AppSettings.gridColumns = n
                                AppSettings.persist()
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = c.divider)
                    SwitchRow(
                        title = "迷你播放条",
                        desc = "列表页底部的正在播放条",
                        checked = AppSettings.showMiniPlayer,
                        onChange = {
                            AppSettings.showMiniPlayer = it
                            AppSettings.persist()
                        },
                    )
                }
            }

            item {
                GroupTitle("歌词")
                SettingCard {
                    SliderRow(
                        title = "字号",
                        value = "${AppSettings.lyricSize}sp",
                        valueFloat = AppSettings.lyricSize.toFloat(),
                        range = 12f..26f,
                        onChange = {
                            AppSettings.lyricSize = it.toInt()
                            AppSettings.persist()
                        },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = c.divider)
                    Text("对齐", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwChip("居中", AppSettings.lyricCenter) {
                            AppSettings.lyricCenter = true
                            AppSettings.persist()
                        }
                        SwChip("左对齐", !AppSettings.lyricCenter) {
                            AppSettings.lyricCenter = false
                            AppSettings.persist()
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = c.divider)
                    SliderRow(
                        title = "时间偏移",
                        value = String.format("%+.1fs", AppSettings.lyricOffsetMs / 1000f),
                        valueFloat = AppSettings.lyricOffsetMs.toFloat(),
                        range = -1500f..1500f,
                        onChange = {
                            AppSettings.lyricOffsetMs = (it / 100f).toInt() * 100
                            AppSettings.persist()
                        },
                    )
                    Text(
                        "歌词比人声早出现就调大偏移，晚了就调小。",
                        fontSize = 11.sp,
                        color = c.sub,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = c.divider)
                    Text("高亮颜色", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Text(
                        "歌词当前行走自己的颜色，不跟主题色。",
                        fontSize = 11.sp,
                        color = c.sub,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LyricPalette.forEachIndexed { i, pair ->
                            val on = AppSettings.lyricColorIndex == i
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(pair.first)
                                    .clickable {
                                        AppSettings.lyricColorIndex = i
                                        AppSettings.persist()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF10131A),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "当前：${LyricPalette[AppSettings.lyricColorIndex.coerceIn(0, LyricPalette.lastIndex)].second}",
                        fontSize = 11.sp,
                        color = c.sub,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                GroupTitle("音质")
                SettingCard {
                    Text("码率上限", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Text(
                        "高于这个值的歌会让服务器转码；原始表示不转码，直接播文件。",
                        fontSize = 11.sp,
                        color = c.sub,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BitrateOptions.forEach { (k, label) ->
                            SwChip(label, AppSettings.bitrate == k) {
                                AppSettings.bitrate = k
                                AppSettings.persist()
                                toast(context, "下一首开始生效")
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = c.divider)
                    SwitchRow(
                        title = "强制 MP3 转码",
                        desc = "遇到 FLAC/ALAC 播不动时打开，兼容性优先",
                        checked = AppSettings.forceMp3,
                        onChange = {
                            AppSettings.forceMp3 = it
                            AppSettings.persist()
                            toast(context, "下一首开始生效")
                        },
                    )
                }
            }

            item {
                GroupTitle("播放")
                SettingCard {
                    Text("播放速度", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeedOptions.forEach { s ->
                            SwChip("${formatSpeed(s)}×", AppSettings.speed == s) {
                                AppSettings.speed = s
                                AppSettings.persist()
                                controller?.setPlaybackSpeed(s)
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = c.divider)
                    Text("快进/快退步长", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeekStepOptions.forEach { s ->
                            SwChip("${s}s", AppSettings.seekStep == s) {
                                AppSettings.seekStep = s
                                AppSettings.persist()
                            }
                        }
                    }
                    Text(
                        "步长在播放页用 -10s / +10s 按钮触发，重启后系统上一首/下一首也会用它。",
                        fontSize = 11.sp,
                        color = c.sub,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = c.divider)
                    SwitchRow(
                        title = "拔耳机自动暂停",
                        desc = "重新打开应用后生效",
                        checked = AppSettings.noisyPause,
                        onChange = {
                            AppSettings.noisyPause = it
                            AppSettings.persist()
                        },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.divider)
                    SwitchRow(
                        title = "被来电打断后自动继续",
                        desc = "音频焦点重新拿到时恢复播放",
                        checked = AppSettings.focusResume,
                        onChange = {
                            AppSettings.focusResume = it
                            AppSettings.persist()
                        },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.divider)
                    SwitchRow(
                        title = "音量平衡",
                        desc = "压平忽大忽小的峰值；设备不支持时会自动跳过",
                        checked = AppSettings.balance,
                        onChange = {
                            AppSettings.balance = it
                            pushPlaybackSettings(context)
                        },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.divider)
                    SwitchRow(
                        title = "淡入淡出",
                        desc = "开歌淡入、结尾淡出，切歌不再“咚”一下",
                        checked = AppSettings.fade,
                        onChange = {
                            AppSettings.fade = it
                            pushPlaybackSettings(context)
                        },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.divider)
                    SwitchRow(
                        title = "无缝播放",
                        desc = "跳过曲间空白，一首接一首不断档",
                        checked = AppSettings.gapless,
                        onChange = {
                            AppSettings.gapless = it
                            pushPlaybackSettings(context)
                        },
                    )
                }
            }

            item {
                GroupTitle("关于")
                SettingCard {
                    Text("Salt Wave 1.4.1", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Text(
                        "Navidrome / Subsonic 客户端 · ExoPlayer(Media3) 内核 · 全部实时串流，不做离线缓存",
                        fontSize = 11.sp,
                        color = c.sub,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun GroupTitle(text: String) {
    val c = sw()
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = c.sub,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    val c = sw()
    Surface(shape = RoundedCornerShape(16.dp), color = c.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), content = content)
    }
}

@Composable
fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = sw()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Text(desc, fontSize = 11.sp, color = c.sub, modifier = Modifier.padding(top = 3.dp))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SliderRow(
    title: String,
    value: String,
    valueFloat: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val c = sw()
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
            Text(value, fontSize = 12.sp, color = c.accent)
        }
        Slider(
            value = valueFloat.coerceIn(range.start, range.endInclusive),
            valueRange = range,
            onValueChange = onChange,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
        )
    }
}

@Composable
fun SwChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = sw()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) c.accentSoft else c.field,
        onClick = onClick,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) c.accent else c.sub,
        )
    }
}

// ---------------------------------------------------------------- 登录

@Composable
fun LoginScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val c = sw()
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(AppSettings.server) }
    var user by remember { mutableStateOf(AppSettings.user) }
    var pass by remember { mutableStateOf(AppSettings.pass) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "返回", tint = c.ink, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Salt Wave", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = c.ink, letterSpacing = 0.5.sp)
        Text("连接你的 Navidrome", fontSize = 13.sp, color = c.sub, modifier = Modifier.padding(top = 6.dp))

        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("服务器地址，如 192.168.1.10:4533") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )

        Button(
            onClick = {
                if (server.isBlank() || user.isBlank()) {
                    message = "地址和用户名都要填"
                    return@Button
                }
                busy = true
                message = null
                scope.launch {
                    try {
                        val api = Subsonic(server.trim(), user.trim(), pass)
                        api.ping()
                        Repo.demo = false
                        Repo.api = api
                        Repo.reset()
                        AppSettings.server = server.trim()
                        AppSettings.user = user.trim()
                        AppSettings.pass = pass
                        AppSettings.demo = false
                        AppSettings.persist()
                        LyricBook.clear()
                        runCatching { Repo.load() }
                        onDone()
                    } catch (e: Exception) {
                        message = "连接失败：${e.message ?: "未知错误"}"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent),
        ) {
            Text(if (busy) "连接中…" else "连接", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        TextButton(
            onClick = {
                Repo.fillDemo()
                AppSettings.demo = true
                AppSettings.persist()
                onDone()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            Text("先看看界面（演示模式）", color = c.accent, fontSize = 13.sp)
        }

        message?.let {
            Text(it, color = Color(0xFFD14343), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }

        Text(
            "支持 Navidrome / Airsonic 等 Subsonic 服务。\n地址可以带端口，例如 http://192.168.1.10:4533。\nHTTP 明文已放行，局域网可直接用。",
            fontSize = 11.sp,
            color = c.sub,
            modifier = Modifier.padding(top = 22.dp),
        )
    }
}

@Composable
fun EmptyBox(text: String) {
    val c = sw()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = c.sub, textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

// ---------------------------------------------------------------- 工具

private fun startPlay(
    controller: MediaController?,
    list: List<Track>,
    index: Int,
    context: Context,
    shuffle: Boolean = false,
) {
    if (Repo.demo) {
        toast(context, "演示模式不能播放，先连接你的 Navidrome")
        return
    }
    if (Repo.api == null) {
        toast(context, "还没连接服务器，先点首页右上角“服务器”")
        return
    }
    if (controller == null) {
        toast(context, "播放器还没准备好，稍后再试")
        return
    }
    if (list.isEmpty()) return
    val items = list.map { t ->
        MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(Repo.streamUrl(t))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(Repo.coverUrl(t.coverId)?.let(Uri::parse))
                    .build(),
            )
            .build()
    }
    controller.shuffleModeEnabled = shuffle
    val start = if (shuffle) Random.nextInt(items.size) else index.coerceIn(0, items.lastIndex)
    controller.setMediaItems(items, start, 0L)
    controller.prepare()
    controller.play()
}

private fun toast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}

private fun mmss(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:" + if (s < 10) "0$s" else "$s"
}

private fun formatSpeed(v: Float): String =
    if (v == v.toInt().toFloat()) "${v.toInt()}.0" else v.toString()

// ---------------------------------------------------------------- 1.4 播放器（黑胶 / 左滑歌词页）

private fun pushPlaybackSettings(context: Context) {
    AppSettings.persist()
    context.sendBroadcast(Intent(PlaybackService.ACTION_RELOAD).setPackage(context.packageName))
}

@Composable
fun PlayerScreen(
    controller: MediaController?,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onQueue: () -> Unit,
) {
    val c = sw()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = c.accent
    val lyricTint = lyricColor()
    var showSleep by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var lyricLines by remember(Now.mediaId) { mutableStateOf<List<LyricLine>>(emptyList()) }
    var lyricLoading by remember(Now.mediaId) { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    val pager = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(Now.mediaId) {
        val id = Now.mediaId ?: return@LaunchedEffect
        lyricLines = emptyList()
        lyricLoading = true
        lyricLines = LyricBook.load(Repo.api, id, Now.artist, Now.title)
        lyricLoading = false
    }

    val duration = Now.duration
    val position = if (dragging) dragValue.toLong() else Now.position
    val active = if (lyricLines.isEmpty()) {
        -1
    } else {
        lyricLines.indexOfLast { it.timeMs <= position + AppSettings.lyricOffsetMs }
    }
    val ink = Color(0xFFF2F5FB)
    val dim = Color(0x8FFFFFFF)

    fun seekTo(ms: Long) {
        val t = if (duration > 0) ms.coerceIn(0, duration) else ms.coerceAtLeast(0)
        controller?.seekTo(t)
        Now.markSeek(t)
    }

    BackHandler(enabled = pager.currentPage == 1) {
        scope.launch { pager.animateScrollToPage(0) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragY.roundToInt()) }
            .background(c.playerBg)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val close = dragY > 140f
                        dragY = 0f
                        if (close) onBack()
                    },
                    onDragCancel = { dragY = 0f },
                ) { change, dy ->
                    if (dy > 0f || dragY > 0f) {
                        dragY = (dragY + dy).coerceAtLeast(0f)
                        change.consume()
                    }
                }
            },
    ) {
        if (Now.artwork != null) {
            AsyncImage(
                model = Now.artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.55f,
                modifier = if (Build.VERSION.SDK_INT >= 31) {
                    Modifier.fillMaxSize().blur(60.dp)
                } else {
                    Modifier.fillMaxSize()
                },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xB30A0D12),
                        0.45f to Color(0xE60A0D12),
                        1f to Color(0xFA0A0D12),
                    ),
                ),
        )

        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        Now.title.ifEmpty { "Salt Wave" },
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ink,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Now.artist.ifEmpty { " " },
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                        fontSize = 11.sp,
                        color = dim,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))

                    Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        VinylDisc(Now.artwork, Now.playing)
                    }

                    Spacer(Modifier.height(4.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                scope.launch { pager.animateScrollToPage(1) }
                            }
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (lyricLines.isEmpty()) {
                            Text(
                                if (lyricLoading) "正在取歌词…" else "点这里看歌词",
                                fontSize = 12.sp,
                                color = dim,
                            )
                            Spacer(Modifier.height(34.dp))
                        } else {
                            val idx = active.coerceAtLeast(0)
                            for (k in -1..1) {
                                val i = idx + k
                                val text = if (i in lyricLines.indices) lyricLines[i].text else " "
                                Text(
                                    text,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    fontSize = if (k == 0) 14.sp else 12.sp,
                                    fontWeight = if (k == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (k == 0) lyricTint else Color(0x59FFFFFF),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    ThinSeekBar(
                        frac = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                        color = accent,
                        onScrub = { f ->
                            dragging = true
                            if (duration > 0) dragValue = (f * duration.toFloat())
                        },
                        onCommit = {
                            val t = dragValue.toLong()
                            dragging = false
                            seekTo(t)
                        },
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(mmss(position), fontSize = 10.sp, color = dim, modifier = Modifier.weight(1f))
                        Text(mmss(duration - position), fontSize = 10.sp, color = dim)
                    }

                    TransportBar(
                        controller = controller,
                        accent = accent,
                        ink = ink,
                        dim = Color(0x8FFFFFFF),
                        onQueue = onQueue,
                    )
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        Now.title.ifEmpty { "Salt Wave" },
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ink,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Now.artist.ifEmpty { " " },
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                        fontSize = 11.sp,
                        color = dim,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        if (lyricLines.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (lyricLoading) "正在取歌词…" else "这首歌没有歌词",
                                    fontSize = 12.sp,
                                    color = dim,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            val offset = AppSettings.lyricOffsetMs
                            val ls = rememberLazyListState()
                            LaunchedEffect(active) {
                                if (active >= 1) ls.animateScrollToItem(active - 1)
                            }
                            // 歌词滚到顶部还继续下拉，就把播放页整体往下拖，松手收回
                            val pullClose = remember(ls) {
                                object : NestedScrollConnection {
                                    override fun onPostScroll(
                                        consumed: Offset,
                                        available: Offset,
                                        source: NestedScrollSource,
                                    ): Offset {
                                        if (available.y > 0f &&
                                            ls.firstVisibleItemIndex == 0 &&
                                            ls.firstVisibleItemScrollOffset == 0 &&
                                            !closing
                                        ) {
                                            dragY = (dragY + available.y).coerceAtLeast(0f)
                                            if (dragY > 170f) {
                                                closing = true
                                                dragY = 0f
                                                onBack()
                                            }
                                            return Offset(0f, available.y)
                                        }
                                        return Offset.Zero
                                    }
                                }
                            }
                            LazyColumn(
                                state = ls,
                                modifier = Modifier.fillMaxSize().nestedScroll(pullClose),
                                contentPadding = PaddingValues(vertical = 60.dp),
                            ) {
                                items(lyricLines.size) { i ->
                                    val on = i == active
                                    Text(
                                        lyricLines[i].text,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                seekTo(lyricLines[i].timeMs - offset)
                                            }
                                            .padding(vertical = 9.dp),
                                        fontSize = (if (on) AppSettings.lyricSize + 6 else AppSettings.lyricSize).sp,
                                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                        color = if (on) lyricTint else Color(0x73FFFFFF),
                                        textAlign = if (AppSettings.lyricCenter) TextAlign.Center else TextAlign.Start,
                                        lineHeight = (AppSettings.lyricSize * 1.4f).sp,
                                    )
                                }
                            }
                        }
                    }

                    ThinSeekBar(
                        frac = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                        color = accent,
                        onScrub = { f ->
                            dragging = true
                            if (duration > 0) dragValue = (f * duration.toFloat())
                        },
                        onCommit = {
                            val t = dragValue.toLong()
                            dragging = false
                            seekTo(t)
                        },
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(mmss(position), fontSize = 10.sp, color = dim, modifier = Modifier.weight(1f))
                        Text(mmss(duration - position), fontSize = 10.sp, color = dim)
                    }
                    TransportBar(
                        controller = controller,
                        accent = accent,
                        ink = ink,
                        dim = Color(0x8FFFFFFF),
                        onQueue = onQueue,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }

    if (showSleep) {
        val minutes = listOf(15, 30, 45, 60)
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("睡眠定时") },
            text = {
                Column {
                    Text("到点自动暂停播放。", fontSize = 12.sp, color = c.sub)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        minutes.forEach { m ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = c.accentSoft,
                                onClick = {
                                    Now.sleepUntil = System.currentTimeMillis() + m * 60_000L
                                    showSleep = false
                                },
                            ) {
                                Text(
                                    "${m}分",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = c.accent,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { Now.sleepUntil = null; showSleep = false }) {
                    Text("关闭定时")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSleep = false }) { Text("取消") }
            },
            containerColor = c.card,
            titleContentColor = c.ink,
            textContentColor = c.ink,
        )
    }
}

@Composable
fun TransportBar(
    controller: MediaController?,
    accent: Color,
    ink: Color,
    dim: Color,
    onQueue: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { controller?.shuffleModeEnabled = !Now.shuffle },
            modifier = Modifier.size(42.dp),
        ) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = "随机",
                tint = if (Now.shuffle) accent else dim,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = { controller?.seekToPreviousMediaItem() },
            modifier = Modifier.size(42.dp),
        ) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首", tint = ink, modifier = Modifier.size(26.dp))
        }
        FilledIconButton(
            onClick = {
                val p = controller ?: return@FilledIconButton
                when {
                    p.isPlaying -> p.pause()
                    p.playbackState == Player.STATE_ENDED -> {
                        p.seekTo(0)
                        p.play()
                    }

                    else -> p.play()
                }
            },
            modifier = Modifier.size(58.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent),
        ) {
            if (Now.buffering) {
                CircProgress(24.dp)
            } else {
                Icon(
                    if (Now.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "播放/暂停",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        IconButton(
            onClick = { controller?.seekToNextMediaItem() },
            modifier = Modifier.size(42.dp),
        ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", tint = ink, modifier = Modifier.size(26.dp))
        }
        IconButton(
            onClick = {
                controller?.repeatMode = when (Now.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            },
            modifier = Modifier.size(42.dp),
        ) {
            Icon(
                if (Now.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "循环",
                tint = if (Now.repeatMode == Player.REPEAT_MODE_OFF) dim else accent,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onQueue, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Rounded.QueueMusic, contentDescription = "播放列表", tint = dim, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun VinylDisc(artwork: Any?, playing: Boolean) {
    val inf = rememberInfiniteTransition(label = "vinyl")
    val angle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "angle",
    )
    val rotation = if (playing) angle else 0f

    Box(
        Modifier.fillMaxWidth(0.74f).aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF2A2F3A), Color(0xFF111419), Color(0xFF07090C)),
                    ),
                ),
        )
        Box(Modifier.fillMaxSize().padding(10.dp).clip(CircleShape).background(Color(0x14FFFFFF)))
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize(0.64f)
                    .clip(CircleShape)
                    .graphicsLayer { rotationZ = rotation },
            )
        } else {
            Box(
                Modifier.fillMaxSize(0.64f).clip(CircleShape).background(Color(0xFF232833)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color(0xFF4A5262), modifier = Modifier.size(40.dp))
            }
        }
        Box(
            Modifier
                .fillMaxSize(0.66f)
                .clip(CircleShape)
                .background(Color(0x0DFFFFFF)),
        )
    }
}

@Composable
fun ThinSeekBar(
    frac: Float,
    color: Color,
    onScrub: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    onScrub((down.position.x / w).coerceIn(0f, 1f))
                    drag(down.id) { change ->
                        onScrub((change.position.x / w).coerceIn(0f, 1f))
                        change.consume()
                    }
                    onCommit()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(CircleShape)
                .background(Color(0x2EFFFFFF)),
        )
        Box(
            Modifier
                .fillMaxWidth(frac.coerceIn(0f, 1f))
                .height(2.5.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}
