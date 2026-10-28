package dev.plume

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MediaListenerService : NotificationListenerService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null

    // Keep tracked controllers even when a media app marks its session inactive on pause
    private val trackedControllers = LinkedHashMap<MediaSession.Token, MediaController>()
    private val controllerCallbacks = HashMap<MediaSession.Token, MediaController.Callback>()
    private var lastValidTrack: MediaTrackInfo? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        syncControllers(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaBridge.requestRefresh = { refreshSessions() }
        refreshSessions()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        extractNotificationController(sbn)
        evaluateSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        evaluateSessions()
    }

    private fun refreshSessions() {
        mainHandler.post {
            try {
                if (sessionManager == null) {
                    sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                    val component = ComponentName(this, MediaListenerService::class.java)
                    sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
                    sessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
                }
                val component = ComponentName(this, MediaListenerService::class.java)
                val active = sessionManager?.getActiveSessions(component)
                syncControllers(active)
                scanNotificationsForMedia()
                evaluateSessions()
            } catch (e: Exception) {
                Log.w("MediaListenerService", "Failed to refresh sessions: ${e.message}")
            }
        }
    }

    private fun scanNotificationsForMedia() {
        try {
            activeNotifications?.forEach { sbn ->
                extractNotificationController(sbn)
            }
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun extractNotificationController(sbn: StatusBarNotification?) {
        val extras = sbn?.notification?.extras ?: return
        val token = if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        } ?: return
        registerController(MediaController(this, token))
    }

    private fun registerController(c: MediaController) {
        val token = c.sessionToken
        if (trackedControllers.containsKey(token)) return
        // Remove older controllers from the same package if a new session token was created
        val samePkgTokens = trackedControllers.entries
            .filter { it.value.packageName == c.packageName && it.key != token }
            .map { it.key }
        samePkgTokens.forEach { removeController(it) }

        val cb = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                evaluateSessions()
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                evaluateSessions()
            }

            override fun onSessionDestroyed() {
                mainHandler.post {
                    removeController(token)
                    evaluateSessions()
                }
            }
        }
        try {
            c.registerCallback(cb, mainHandler)
            trackedControllers[token] = c
            controllerCallbacks[token] = cb
        } catch (_: Exception) {}
    }

    private fun removeController(token: MediaSession.Token) {
        val c = trackedControllers.remove(token)
        val cb = controllerCallbacks.remove(token)
        if (c != null && cb != null) {
            try { c.unregisterCallback(cb) } catch (_: Exception) {}
        }
        if (lastValidTrack?.controller?.sessionToken == token) {
            lastValidTrack = null
        }
    }

    private fun syncControllers(controllers: List<MediaController>?) {
        mainHandler.post {
            // Add/refresh active controllers to the front of priority without dropping paused ones!
            controllers?.forEach { c ->
                registerController(c)
            }
            evaluateSessions()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (MediaBridge.requestRefresh != null) MediaBridge.requestRefresh = null
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        if (MediaBridge.requestRefresh != null) MediaBridge.requestRefresh = null
        clearControllers()
    }

    private fun clearControllers() {
        val tokens = trackedControllers.keys.toList()
        tokens.forEach { removeController(it) }
        lastValidTrack = null
        MediaBridge.updateTrack(null)
    }

    private fun isActivePlaybackState(state: Int?): Boolean {
        return state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING ||
            state == PlaybackState.STATE_CONNECTING ||
            state == PlaybackState.STATE_FAST_FORWARDING ||
            state == PlaybackState.STATE_REWINDING ||
            state == PlaybackState.STATE_SKIPPING_TO_NEXT ||
            state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS
    }

    private fun evaluateSessions() {
        mainHandler.post {
            val all = trackedControllers.values.toList().asReversed()
            var chosen: MediaController? = null

            // 1. Prefer actively playing / buffering / skipping sessions
            for (c in all) {
                if (isActivePlaybackState(c.playbackState?.state)) {
                    chosen = c
                    break
                }
            }
            // 2. Otherwise prefer paused sessions that have metadata
            if (chosen == null) {
                for (c in all) {
                    val state = c.playbackState?.state
                    if (state == PlaybackState.STATE_PAUSED && hasMetadataTitle(c)) {
                        chosen = c
                        break
                    }
                }
            }
            // 3. Otherwise any tracked session with valid metadata that isn't explicitly STOPPED/ERROR
            if (chosen == null) {
                for (c in all) {
                    val state = c.playbackState?.state
                    if (state != PlaybackState.STATE_STOPPED && state != PlaybackState.STATE_ERROR && hasMetadataTitle(c)) {
                        chosen = c
                        break
                    }
                }
            }

            if (chosen != null) {
                val meta = chosen.metadata
                val title = (meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: meta?.description?.title?.toString()
                    ?: "").trim()
                val artist = (meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: meta?.description?.subtitle?.toString()
                    ?: "").trim()
                val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: meta?.description?.iconBitmap

                val state = chosen.playbackState?.state
                val isPlaying = isActivePlaybackState(state)

                if (title.isNotEmpty()) {
                    val info = MediaTrackInfo(
                        title = title,
                        artist = artist,
                        art = art ?: lastValidTrack?.takeIf { it.title == title }?.art,
                        packageName = chosen.packageName ?: "",
                        isPlaying = isPlaying,
                        controller = chosen
                    )
                    lastValidTrack = info
                    MediaBridge.updateTrack(info)
                    return@post
                } else if (lastValidTrack != null && lastValidTrack?.packageName == chosen.packageName) {
                    // Transient metadata gap while skipping tracks: preserve last track with updated state
                    val updated = lastValidTrack!!.copy(isPlaying = isPlaying, controller = chosen)
                    lastValidTrack = updated
                    MediaBridge.updateTrack(updated)
                    return@post
                }
            }

            lastValidTrack = null
            MediaBridge.updateTrack(null)
        }
    }

    private fun hasMetadataTitle(c: MediaController): Boolean {
        val meta = c.metadata ?: return false
        val t = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.description?.title?.toString()
            ?: ""
        return t.isNotBlank()
    }
}
