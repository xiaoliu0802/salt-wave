package com.saltwave.app

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Media3 播放服务：ExoPlayer + MediaSession，通知栏与锁屏控制由 Media3 接管。 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

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

        session = MediaSession.Builder(this, player).build()
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
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
