package com.saltwave.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Media3 播放服务：ExoPlayer + MediaSession，通知栏与锁屏控制由 Media3 接管。 */
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_RELOAD = "com.saltwave.app.RELOAD_SETTINGS"
    }

    private var session: MediaSession? = null
    private var playerRef: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var fadeJob: Job? = null
    private var fadingOut = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            applySettings()
        }
    }

    private val fadeListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            fadingOut = false
            val p = playerRef ?: return
            if (AppSettings.fade) {
                p.volume = 0f
                fadeTo(1f, 700)
            } else {
                p.volume = 1f
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val p = playerRef ?: return
            if (isPlaying) {
                if (AppSettings.fade && p.volume < 1f) fadeTo(1f, 500)
            } else {
                p.volume = 1f
                fadingOut = false
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                playerRef?.volume = 1f
                fadingOut = false
            }
            // 音频会话到这时才有，音量平衡在这里补挂
            val p = playerRef ?: return
            if (AppSettings.balance) VolumeBalance.apply(p, true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(applicationContext)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                20_000,   // 最短缓冲
                90_000,   // 最长缓冲
                800,      // 够播就立刻出声（默认 2s，网络歌等太久）
                2_000,    // 掉线后回填门槛
            )
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                AppSettings.focusResume,
            )
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setHandleAudioBecomingNoisy(AppSettings.noisyPause)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(AppSettings.seekStep * 1000L)
            .setSeekForwardIncrementMs(AppSettings.seekStep * 1000L)
            .build()

        playerRef = player
        player.addListener(fadeListener)
        player.skipSilenceEnabled = AppSettings.gapless
        session = MediaSession.Builder(this, player).build()

        val filter = IntentFilter(ACTION_RELOAD)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        applySettings()
        startFadeWatch()
    }

    private fun applySettings() {
        val p = playerRef ?: return
        p.skipSilenceEnabled = AppSettings.gapless
        VolumeBalance.apply(p, AppSettings.balance)
        if (!AppSettings.fade) p.volume = 1f
    }

    private fun startFadeWatch() {
        scope.launch {
            while (true) {
                delay(250)
                val p = playerRef ?: continue
                if (!AppSettings.fade || !p.isPlaying || fadingOut) continue
                val dur = p.duration
                if (dur <= 0 || dur == C.TIME_UNSET) continue
                val left = dur - p.currentPosition
                if (left in 1..1200L) {
                    fadingOut = true
                    fadeTo(0.06f, left)
                }
            }
        }
    }

    private fun fadeTo(target: Float, ms: Long) {
        val p = playerRef ?: return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val from = p.volume
            val steps = 12
            val stepMs = (ms / steps).coerceAtLeast(15)
            for (i in 1..steps) {
                p.volume = from + (target - from) * (i.toFloat() / steps)
                delay(stepMs)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        scope.cancel()
        VolumeBalance.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
