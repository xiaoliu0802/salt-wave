package com.saltwave.app

import android.media.audiofx.DynamicsProcessing
import androidx.media3.exoplayer.ExoPlayer

/**
 * 音量平衡：给播放器的音频会话挂一个限幅/动态压缩器，
 * 把忽大忽小的峰值压平。设备不支持时静默降级，不崩。
 */
object VolumeBalance {

    private var effect: DynamicsProcessing? = null

    fun apply(player: ExoPlayer, on: Boolean) {
        release()
        if (!on) return
        val session = player.audioSessionId
        if (session == 0) return
        try {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                2,
                false, 0,
                false, 0,
                false, 0,
                true,
            ).build()
            effect = DynamicsProcessing(0, session, cfg).also { it.enabled = true }
        } catch (t: Throwable) {
            effect = null
        }
    }

    fun release() {
        runCatching { effect?.release() }
        effect = null
    }
}
