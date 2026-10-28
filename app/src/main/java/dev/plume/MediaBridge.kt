package dev.plume

import android.graphics.Bitmap
import android.media.session.MediaController

data class MediaTrackInfo(
    val title: String,
    val artist: String,
    val art: Bitmap? = null,
    val packageName: String = "",
    val isPlaying: Boolean = false,
    val controller: MediaController? = null
)

object MediaBridge {
    @Volatile
    var currentTrack: MediaTrackInfo? = null
        private set

    var onMediaChanged: ((MediaTrackInfo?) -> Unit)? = null

    fun updateTrack(track: MediaTrackInfo?) {
        currentTrack = track
        onMediaChanged?.invoke(track)
    }
}
