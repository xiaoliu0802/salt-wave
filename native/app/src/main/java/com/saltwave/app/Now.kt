package com.saltwave.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController

data class QueueEntry(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
)

/** 播放状态的唯一可信源，UI 只读这里。 */
object Now {

    var mediaId by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var album by mutableStateOf("")
    var artwork by mutableStateOf<String?>(null)
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var shuffle by mutableStateOf(false)
    var hasNext by mutableStateOf(false)
    var hasPrev by mutableStateOf(false)
    var index by mutableIntStateOf(0)
    var speed by mutableFloatStateOf(1f)
    var queue by mutableStateOf<List<QueueEntry>>(emptyList())
    var sleepUntil by mutableStateOf<Long?>(null)

    /** 跳转后短时间锁住进度显示，避免进度条被旧位置拽回去。 */
    var seekTarget by mutableStateOf<Long?>(null)
    var seekAt by mutableLongStateOf(0L)

    fun markSeek(target: Long) {
        seekTarget = target
        seekAt = System.currentTimeMillis()
        position = target
    }

    private var bound = false

    fun bind(c: MediaController) {
        if (bound) return
        bound = true
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                sync(player)
            }
        })
        sync(c)
        c.setPlaybackSpeed(AppSettings.speed)
    }

    fun sync(player: Player) {
        title = player.mediaMetadata.title?.toString().orEmpty()
        artist = player.mediaMetadata.artist?.toString().orEmpty()
        album = player.mediaMetadata.albumTitle?.toString().orEmpty()
        artwork = player.mediaMetadata.artworkUri?.toString()
        playing = player.isPlaying
        buffering = player.playbackState == Player.STATE_BUFFERING
        duration = player.duration.coerceAtLeast(0L)
        position = player.currentPosition.coerceAtLeast(0L)
        repeatMode = player.repeatMode
        shuffle = player.shuffleModeEnabled
        index = player.currentMediaItemIndex
        hasNext = player.hasNextMediaItem()
        hasPrev = player.hasPreviousMediaItem()
        speed = player.playbackParameters.speed
        mediaId = player.currentMediaItem?.mediaId
        queue = (0 until player.mediaItemCount).map { i ->
            val item = player.getMediaItemAt(i)
            QueueEntry(
                id = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                durationMs = 0L,
            )
        }
    }

    fun resetPosition() {
        position = 0L
        duration = 0L
    }
}
