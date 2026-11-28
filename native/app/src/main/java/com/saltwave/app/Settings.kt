package com.saltwave.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 全部可调项集中在这里，改完即写盘。 */
object AppSettings {

    private const val FILE = "sw"
    private var prefs: SharedPreferences? = null

    // 账号
    var server by mutableStateOf("")
    var user by mutableStateOf("")
    var pass by mutableStateOf("")
    var demo by mutableStateOf(false)

    // 用户界面
    var themeMode by mutableIntStateOf(0)      // 0 跟随系统 / 1 浅色 / 2 深色
    var accentIndex by mutableIntStateOf(0)
    var coverRadius by mutableIntStateOf(14)
    var gridColumns by mutableIntStateOf(2)
    var showMiniPlayer by mutableStateOf(true)

    // 歌词
    var lyricSize by mutableIntStateOf(17)
    var lyricCenter by mutableStateOf(false)
    var lyricOffsetMs by mutableIntStateOf(0)
    var showLyricPanel by mutableStateOf(true)

    // 音质
    var bitrate by mutableIntStateOf(0)        // kbps，0 表示不转码
    var forceMp3 by mutableStateOf(false)

    // 播放
    var speed by mutableFloatStateOf(1f)
    var seekStep by mutableIntStateOf(10)
    var noisyPause by mutableStateOf(true)
    var focusResume by mutableStateOf(true)

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        server = p.getString("server", "").orEmpty()
        user = p.getString("user", "").orEmpty()
        pass = p.getString("pass", "").orEmpty()
        demo = p.getBoolean("demo", false)
        themeMode = p.getInt("ui.theme", 0)
        accentIndex = p.getInt("ui.accent", 0)
        coverRadius = p.getInt("ui.radius", 14)
        gridColumns = p.getInt("ui.columns", 2)
        showMiniPlayer = p.getBoolean("ui.mini", true)
        lyricSize = p.getInt("ly.size", 17)
        lyricCenter = p.getBoolean("ly.center", false)
        lyricOffsetMs = p.getInt("ly.offset", 0)
        showLyricPanel = p.getBoolean("ly.panel", true)
        bitrate = p.getInt("au.bitrate", 0)
        forceMp3 = p.getBoolean("au.mp3", false)
        speed = p.getFloat("pb.speed", 1f)
        seekStep = p.getInt("pb.step", 10)
        noisyPause = p.getBoolean("pb.noisy", true)
        focusResume = p.getBoolean("pb.focus", true)
    }

    fun persist() {
        prefs?.edit()
            ?.putString("server", server)
            ?.putString("user", user)
            ?.putString("pass", pass)
            ?.putBoolean("demo", demo)
            ?.putInt("ui.theme", themeMode)
            ?.putInt("ui.accent", accentIndex)
            ?.putInt("ui.radius", coverRadius)
            ?.putInt("ui.columns", gridColumns)
            ?.putBoolean("ui.mini", showMiniPlayer)
            ?.putInt("ly.size", lyricSize)
            ?.putBoolean("ly.center", lyricCenter)
            ?.putInt("ly.offset", lyricOffsetMs)
            ?.putBoolean("ly.panel", showLyricPanel)
            ?.putInt("au.bitrate", bitrate)
            ?.putBoolean("au.mp3", forceMp3)
            ?.putFloat("pb.speed", speed)
            ?.putInt("pb.step", seekStep)
            ?.putBoolean("pb.noisy", noisyPause)
            ?.putBoolean("pb.focus", focusResume)
            ?.apply()
    }

    fun bitrateText(): String = if (bitrate <= 0) "原始" else "${bitrate}k"
}

/** 音质选项。 */
val BitrateOptions = listOf(0 to "原始", 320 to "320k", 192 to "192k", 128 to "128k")
val SpeedOptions = listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f)
val SeekStepOptions = listOf(5, 10, 15, 30)
