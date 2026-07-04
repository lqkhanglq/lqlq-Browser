package com.lqlq.browser

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Foreground service giữ cho việc đọc truyện TXT (TTS) và nhạc/video nền
 * tiếp tục chạy khi người dùng chuyển sang ứng dụng khác hoặc tắt màn hình.
 *
 * Hiện thanh thông báo media với nút: lùi câu / phát-tạm dừng / tới câu / đóng
 * (giống thanh thông báo T2S trong ảnh mẫu của người dùng).
 */
class PlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "lqlq_playback"
        private const val NOTIFICATION_ID = 1023

        const val ACTION_UPDATE = "com.lqlq.browser.action.UPDATE"
        const val ACTION_STOP_KIND = "com.lqlq.browser.action.STOP_KIND"
        const val ACTION_PREV = "com.lqlq.browser.action.PREV"
        const val ACTION_TOGGLE = "com.lqlq.browser.action.TOGGLE"
        const val ACTION_NEXT = "com.lqlq.browser.action.NEXT"
        const val ACTION_CLOSE = "com.lqlq.browser.action.CLOSE"

        const val EXTRA_KIND = "kind"          // "reader" hoặc "media"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_PLAYING = "playing"

        fun update(
            context: Context,
            kind: String,
            title: String,
            text: String,
            playing: Boolean
        ) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_KIND, kind)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_PLAYING, playing)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopKind(context: Context, kind: String) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_STOP_KIND
                putExtra(EXTRA_KIND, kind)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // Service chưa chạy — không cần làm gì.
            }
        }
    }

    private data class SessionState(
        var active: Boolean = false,
        var playing: Boolean = false,
        var title: String = "",
        var text: String = ""
    )

    private val reader = SessionState()
    private val media = SessionState()

    /** Phiên đang được ưu tiên hiển thị trên thông báo. */
    private var frontKind: String = "reader"

    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Mất focus: tạm dừng phiên đang phát để không chồng tiếng.
                if (stateFor(frontKind).playing) {
                    sendToggle(frontKind)
                }
            }
            else -> Unit
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "lqlqPlayback").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = sendToggle(frontKind)
                override fun onPause() = sendToggle(frontKind)
                override fun onSkipToNext() = sendNext(frontKind)
                override fun onSkipToPrevious() = sendPrev(frontKind)
                override fun onStop() = sendClose(frontKind)
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> {
                val kind = intent.getStringExtra(EXTRA_KIND) ?: "reader"
                val state = stateFor(kind)
                state.active = true
                state.playing = intent.getBooleanExtra(EXTRA_PLAYING, false)
                state.title = intent.getStringExtra(EXTRA_TITLE) ?: ""
                state.text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                frontKind = kind
                Log.d("lqlqPlayback", "ACTION_UPDATE kind=$kind playing=${state.playing} title=${state.title}")
                refresh()
            }
            ACTION_STOP_KIND -> {
                val kind = intent.getStringExtra(EXTRA_KIND) ?: "reader"
                stateFor(kind).apply {
                    active = false
                    playing = false
                }
                if (!reader.active && !media.active) {
                    shutdown()
                    return START_NOT_STICKY
                }
                frontKind = if (reader.active) "reader" else "media"
                refresh()
            }
            ACTION_PREV -> sendPrev(frontKind)
            ACTION_TOGGLE -> sendToggle(frontKind)
            ACTION_NEXT -> sendNext(frontKind)
            ACTION_CLOSE -> {
                sendClose(frontKind)
                stateFor(frontKind).apply {
                    active = false
                    playing = false
                }
                if (!reader.active && !media.active) {
                    shutdown()
                    return START_NOT_STICKY
                }
                frontKind = if (reader.active) "reader" else "media"
                refresh()
            }
        }
        return START_NOT_STICKY
    }

    private fun stateFor(kind: String) = if (kind == "media") media else reader

    private fun sendToggle(kind: String) {
        if (kind == "media") BridgeHub.mediaCommand("toggle")
        else BridgeHub.readerCommand("toggle")
    }

    private fun sendNext(kind: String) {
        if (kind == "media") BridgeHub.mediaCommand("toggle")
        else BridgeHub.readerCommand("next")
    }

    private fun sendPrev(kind: String) {
        if (kind == "media") BridgeHub.mediaCommand("toggle")
        else BridgeHub.readerCommand("prev")
    }

    private fun sendClose(kind: String) {
        if (kind == "media") BridgeHub.mediaCommand("stop")
        else BridgeHub.readerCommand("stop")
    }

    private fun refresh() {
        val anyPlaying = (reader.active && reader.playing) || (media.active && media.playing)

        if (anyPlaying) {
            acquireAudioFocus()
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }

        updateMediaSession()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d("lqlqPlayback", "startForeground OK frontKind=$frontKind anyPlaying=$anyPlaying")
        } catch (error: Exception) {
            // Ví dụ ForegroundServiceStartNotAllowedException (Android 12+) khi
            // service được khởi động không đúng lúc app đang ở nền quá lâu.
            Log.w("lqlqPlayback", "startForeground thất bại", error)
        }
    }

    private fun updateMediaSession() {
        val state = stateFor(frontKind)
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(
                    MediaMetadataCompat.METADATA_KEY_TITLE,
                    state.title.ifBlank {
                        if (frontKind == "media") "Nhạc và video nền" else "Đọc truyện TXT"
                    }
                )
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.text)
                .build()
        )
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP
                )
                .setState(
                    if (state.playing) PlaybackStateCompat.STATE_PLAYING
                    else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1f
                )
                .build()
        )
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(): Notification {
        val state = stateFor(frontKind)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isReader = frontKind == "reader"
        val title = state.title.ifBlank {
            if (isReader) "Đọc truyện TXT" else "Nhạc và video nền"
        }
        val text = state.text.ifBlank {
            if (state.playing) "Đang phát" else "Đã tạm dừng"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lqlq)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(state.playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

        if (isReader) {
            builder.addAction(
                android.R.drawable.ic_media_previous, "Câu trước", actionIntent(ACTION_PREV)
            )
        }
        builder.addAction(
            if (state.playing) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play,
            if (state.playing) "Tạm dừng" else "Phát",
            actionIntent(ACTION_TOGGLE)
        )
        if (isReader) {
            builder.addAction(
                android.R.drawable.ic_media_next, "Câu sau", actionIntent(ACTION_NEXT)
            )
        }
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel, "Đóng", actionIntent(ACTION_CLOSE)
        )

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
        style.setShowActionsInCompactView(
            *(if (isReader) intArrayOf(0, 1, 2) else intArrayOf(0, 1))
        )
        builder.setStyle(style)

        return builder.build()
    }

    private fun acquireAudioFocus() {
        if (hasAudioFocus) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        hasAudioFocus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun releaseAudioFocus() {
        if (!hasAudioFocus) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        hasAudioFocus = false
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "lqlq:playback"
        ).apply { acquire(6 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun shutdown() {
        releaseWakeLock()
        releaseAudioFocus()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        releaseAudioFocus()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
