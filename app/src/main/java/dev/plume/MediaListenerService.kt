package dev.plume

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log

class MediaListenerService : NotificationListenerService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private val activeControllers = mutableListOf<MediaController>()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            evaluateSessions()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            evaluateSessions()
        }

        override fun onSessionDestroyed() {
            evaluateSessions()
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateControllers(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val component = ComponentName(this, MediaListenerService::class.java)
            sessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
            val initial = sessionManager?.getActiveSessions(component)
            updateControllers(initial)
        } catch (e: Exception) {
            Log.w("MediaListenerService", "Failed to get active sessions: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
            clearControllers()
        } catch (e: Exception) {
            Log.w("MediaListenerService", "Error during disconnect: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearControllers()
    }

    private fun clearControllers() {
        activeControllers.forEach {
            try { it.unregisterCallback(callback) } catch (e: Exception) {}
        }
        activeControllers.clear()
        MediaBridge.updateTrack(null)
    }

    private fun updateControllers(controllers: List<MediaController>?) {
        activeControllers.forEach {
            try { it.unregisterCallback(callback) } catch (e: Exception) {}
        }
        activeControllers.clear()
        controllers?.forEach {
            try {
                it.registerCallback(callback, mainHandler)
                activeControllers.add(it)
            } catch (e: Exception) {}
        }
        evaluateSessions()
    }

    private fun evaluateSessions() {
        mainHandler.post {
            var playingController: MediaController? = null
            for (c in activeControllers) {
                val state = c.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_FAST_FORWARDING || state == PlaybackState.STATE_REWINDING) {
                    playingController = c
                    break
                }
            }
            if (playingController == null) {
                for (c in activeControllers) {
                    val state = c.playbackState?.state
                    if (state == PlaybackState.STATE_PAUSED) {
                        playingController = c
                        break
                    }
                }
            }

            if (playingController != null) {
                val meta = playingController.metadata
                val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: meta?.description?.title?.toString()
                    ?: ""
                val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta?.description?.subtitle?.toString()
                    ?: ""
                val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: meta?.description?.iconBitmap

                val isPlaying = playingController.playbackState?.state == PlaybackState.STATE_PLAYING

                if (title.isNotEmpty()) {
                    MediaBridge.updateTrack(
                        MediaTrackInfo(
                            title = title,
                            artist = artist,
                            art = art,
                            packageName = playingController.packageName ?: "",
                            isPlaying = isPlaying,
                            controller = playingController
                        )
                    )
                    return@post
                }
            }

            MediaBridge.updateTrack(null)
        }
    }
}
