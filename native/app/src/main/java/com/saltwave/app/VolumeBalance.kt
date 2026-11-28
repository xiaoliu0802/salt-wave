package com.saltwave.app

import android.media.audiofx.DynamicsProcessing
import androidx.media3.exoplayer.ExoPlayer

/**
 * 音量平衡：给播放器的音频会话挂一个限幅/动态压缩器，
 * 把忽大忽小的峰值压平。设备不支持时静默降级，不崩。
 */
object VolumeBalance {

    private var effect: DynamicsProcessing? = null
    private var session = 0

    fun apply(player: ExoPlayer, on: Boolean) {
        if (!on) {
            release()
            return
        }
        val s = player.audioSessionId
        // 音频会话是播起来之后才生成的，太早拿不到就等下次 READY 再挂
        if (s == 0) return
        if (effect != null && session == s) return
        release()
        try {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                2,
                false, 0,
                false, 0,
                false, 0,
                true,
            ).build()
            effect = DynamicsProcessing(0, s, cfg).also { it.enabled = true }
            session = s
        } catch (t: Throwable) {
            effect = null
            session = 0
        }
    }

    fun release() {
        runCatching { effect?.release() }
        effect = null
        session = 0
    }
}
