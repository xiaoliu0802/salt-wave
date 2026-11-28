package com.saltwave.app

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    data object Login : Screen
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private var controller by mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("sw", MODE_PRIVATE)
        restore()

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener(
            {
                controller = try {
                    future.get()
                } catch (e: Exception) {
                    null
                }
            },
            ContextCompat.getMainExecutor(this),
        )

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
        }

        setContent {
            SaltTheme {
                AppRoot(controller = controller, prefs = prefs)
            }
        }
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }

    private fun restore() {
        if (prefs.getBoolean("demo", false)) {
            Repo.fillDemo()
            return
        }
        val server = prefs.getString("server", "").orEmpty()
        if (server.isNotEmpty()) {
            Repo.api = Subsonic(
                server,
                prefs.getString("user", "").orEmpty(),
                prefs.getString("pass", "").orEmpty(),
            )
        }
    }
}

@Composable
fun AppRoot(controller: MediaController?, prefs: SharedPreferences) {
    val context = LocalContext.current
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home(0))) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            tick++
        }
    }

    val current = stack.last()

    LaunchedEffect(current) {
        if (current is Screen.Home && Repo.api != null && !Repo.loaded) {
            runCatching { Repo.load() }
        }
    }

    BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }

    val dark = current is Screen.Player
    SideEffect {
        val window = (context as Activity).window
        window.statusBarColor = if (dark) 0xFF0B0E14.toInt() else 0xFFF5F6F8.toInt()
        window.navigationBarColor = if (dark) 0xFF0B0E14.toInt() else 0xFFF5F6F8.toInt()
        var flags = 0
        if (!dark) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    Box(Modifier.fillMaxSize().background(if (dark) DarkBg else PageBg)) {
        when (current) {
            is Screen.Home -> HomeScreen(
                tab = current.tab,
                onTab = { t -> stack = stack.dropLast(1) + Screen.Home(t) },
                onServer = { stack = stack + Screen.Login },
                onAlbum = { a ->
                    stack = stack + Screen.Detail(false, a.id, a.name, a.artist, a.coverId, a.demoRes, a.count)
                },
                onPlaylist = { p ->
                    stack = stack + Screen.Detail(true, p.id, p.name, "", p.coverId, p.demoRes, p.count)
                },
            )

            is Screen.Login -> LoginScreen(
                prefs = prefs,
                onDone = { stack = listOf(Screen.Home(0)) },
            )

            is Screen.Detail -> DetailScreen(
                screen = current,
                controller = controller,
                onBack = { stack = stack.dropLast(1) },
            )

            Screen.Player -> PlayerScreen(
                controller = controller,
                onBack = { stack = stack.dropLast(1) },
            )
        }

        if (!dark && controller?.currentMediaItem != null) {
            MiniPlayer(
                controller = controller,
                modifier = Modifier.align(Alignment.BottomCenter),
                onClick = { if (stack.last() !is Screen.Player) stack = stack + Screen.Player },
            )
        }
    }
}

@Composable
fun HomeScreen(
    tab: Int,
    onTab: (Int) -> Unit,
    onServer: () -> Unit,
    onAlbum: (Album) -> Unit,
    onPlaylist: (Playlist) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Salt Wave",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Accent.copy(alpha = 0.08f),
                onClick = onServer,
            ) {
                Text(
                    if (Repo.api != null) "服务器" else "未连接",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Accent,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row {
            val names = listOf("专辑", "歌单", "收藏")
            names.forEachIndexed { i, n ->
                val on = tab == i
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (on) Accent else Color(0x11000000),
                    onClick = { onTab(i) },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(
                        n,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (on) Color.White else Sub,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        when {
            Repo.api == null && !Repo.demo -> EmptyBox("还没有连接服务器\n点右上角“服务器”填入地址后就能看到你的曲库")
            Repo.loading -> EmptyBox("正在从服务器读取…")
            else -> {
                val items: List<Any> = when (tab) {
                    0 -> Repo.albums.toList()
                    1 -> Repo.playlists.toList()
                    else -> Repo.starred.toList()
                }
                if (items.isEmpty()) {
                    EmptyBox(if (tab == 2) "还没有收藏的专辑" else "这里是空的")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp),
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
    Column(Modifier.clickable { onClick(album) }) {
        Box {
            CoverImage(album.coverId, album.demoRes, Modifier.fillMaxWidth().aspectRatio(1f), 14.dp)
            Badge("专辑", Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(
            album.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (album.artist.isEmpty()) "${album.count} 首" else "${album.artist} · ${album.count} 首",
            fontSize = 11.sp,
            color = Sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: (Playlist) -> Unit) {
    Column(Modifier.clickable { onClick(playlist) }) {
        Box {
            CoverImage(playlist.coverId, playlist.demoRes, Modifier.fillMaxWidth().aspectRatio(1f), 14.dp)
            Badge("歌单", Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(
            playlist.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text("${playlist.count} 首", fontSize = 11.sp, color = Sub, maxLines = 1)
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
fun CoverImage(coverId: String?, demoRes: Int?, modifier: Modifier = Modifier, radius: Dp = 14.dp) {
    val base = modifier.clip(RoundedCornerShape(radius)).background(Placeholder)
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
fun DetailScreen(screen: Screen.Detail, controller: MediaController?, onBack: () -> Unit) {
    var songs by remember(screen.id) { mutableStateOf(Repo.cachedSongs(screen.playlist, screen.id)) }
    var error by remember(screen.id) { mutableStateOf<String?>(null) }
    var loading by remember(screen.id) { mutableStateOf(false) }
    val context = LocalContext.current

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp, 40.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("‹", fontSize = 26.sp, color = Ink)
            }
            Text(
                screen.name,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(34.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(screen.coverId, screen.demoRes, Modifier.size(84.dp), 12.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    screen.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (screen.artist.isNotEmpty()) {
                    Text(
                        screen.artist,
                        fontSize = 12.sp,
                        color = Sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    "${list?.size ?: screen.count} 首",
                    fontSize = 11.sp,
                    color = Sub,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Button(
            onClick = {
                if (list.isNullOrEmpty()) {
                    toast(context, "曲目还没加载出来")
                } else {
                    startPlay(controller, list, 0, context)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp).height(46.dp),
            shape = RoundedCornerShape(23.dp),
        ) {
            Text("播放全部", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        when {
            list == null && loading -> EmptyBox("正在加载曲目…")
            list == null && error != null -> EmptyBox("读取曲目失败：$error")
            list.isNullOrEmpty() -> EmptyBox("这个列表里没有歌曲")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp),
            ) {
                items(list) { track ->
                    val index = list.indexOf(track)
                    TrackRow(track) { startPlay(controller, list, index, context) }
                }
            }
        }
    }
}

@Composable
fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(track.coverId, track.demoRes, Modifier.size(44.dp), 8.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                fontSize = 11.sp,
                color = Sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(mmss(track.duration * 1000L), fontSize = 11.sp, color = Sub)
    }
}

@Composable
fun PlayerScreen(controller: MediaController?, onBack: () -> Unit) {
    val meta = controller?.mediaMetadata
    val duration = controller?.duration?.takeIf { it > 0 } ?: 0L
    val position = controller?.currentPosition ?: 0L
    val playing = controller?.isPlaying == true

    Column(Modifier.fillMaxSize().background(DarkBg).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp, 40.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("‹", fontSize = 26.sp, color = Color(0xFFE6EAF2))
            }
            Text(
                "正在播放",
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = DarkSub,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(34.dp))
        }

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = meta?.artworkUri?.toString(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF161A22)),
            )
        }

        Text(
            meta?.title?.toString() ?: "还没有正在播放的歌",
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            meta?.artist?.toString() ?: "",
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            fontSize = 12.sp,
            color = DarkSub,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(mmss(position), fontSize = 10.sp, color = DarkSub, modifier = Modifier.weight(1f))
            Text(mmss(duration), fontSize = 10.sp, color = DarkSub)
        }
        Slider(
            value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
            valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
            onValueChange = { controller?.seekTo(it.toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Accent,
                inactiveTrackColor = Color(0xFF2A303C),
            ),
        )

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { controller?.seekToPreviousMediaItem() }) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    tint = Color(0xFFCBD3E0),
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(26.dp))
            FilledIconButton(
                onClick = { if (playing) controller?.pause() else controller?.play() },
                modifier = Modifier.size(66.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent),
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(26.dp))
            IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    tint = Color(0xFFCBD3E0),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(controller: MediaController, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val meta = controller.mediaMetadata
    val dur = controller.duration
    val pos = controller.currentPosition
    val frac = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 10.dp,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Accent,
                trackColor = Color(0xFFE6EAF1),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = meta.artworkUri?.toString(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Placeholder),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        meta.title?.toString() ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        meta.artist?.toString() ?: "",
                        fontSize = 11.sp,
                        color = Sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { if (controller.isPlaying) controller.pause() else controller.play() }) {
                    Icon(
                        if (controller.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Accent,
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(prefs: SharedPreferences, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(prefs.getString("server", "").orEmpty()) }
    var user by remember { mutableStateOf(prefs.getString("user", "").orEmpty()) }
    var pass by remember { mutableStateOf(prefs.getString("pass", "").orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(56.dp))
        Text("Salt Wave", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = 0.5.sp)
        Text("连接你的 Navidrome", fontSize = 13.sp, color = Sub, modifier = Modifier.padding(top = 6.dp))

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
                        Repo.api = api
                        Repo.reset()
                        prefs.edit()
                            .putString("server", server.trim())
                            .putString("user", user.trim())
                            .putString("pass", pass)
                            .putBoolean("demo", false)
                            .apply()
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
        ) {
            Text(if (busy) "连接中…" else "连接", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        TextButton(
            onClick = {
                Repo.fillDemo()
                prefs.edit().putBoolean("demo", true).apply()
                onDone()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            Text("先看看界面（演示模式）", color = Accent, fontSize = 13.sp)
        }

        message?.let {
            Text(it, color = Color(0xFFD14343), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }

        Text(
            "支持 Navidrome / Airsonic 等 Subsonic 服务。\n地址可以带端口，例如 http://192.168.1.10:4533。\nHTTP 明文已放行，局域网可直接用。",
            fontSize = 11.sp,
            color = Sub,
            modifier = Modifier.padding(top = 22.dp),
        )
    }
}

@Composable
fun EmptyBox(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = Sub, textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

private fun startPlay(controller: MediaController?, list: List<Track>, index: Int, context: Context) {
    if (Repo.demo) {
        toast(context, "演示模式不能播放，先连接你的 Navidrome")
        return
    }
    if (controller == null) {
        toast(context, "播放器还没准备好，稍后再试")
        return
    }
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
    controller.setMediaItems(items, index, 0L)
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
