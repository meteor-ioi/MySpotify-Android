package dev.margbug.myspotify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import java.net.URL
import kotlin.concurrent.thread

class MediaPlaybackService : Service() {

    private val binder = LocalBinder()
    private lateinit var mediaSession: MediaSessionCompat
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var currentTitle = "Spotify Web"
    private var currentArtist = "准备就绪"
    private var currentCoverUrl = ""
    private var currentCoverBitmap: Bitmap? = null
    private var isPlaying = false

    var onControlAction: ((action: String) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MySpotify::WakeLock").apply {
            setReferenceCounted(false)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MySpotify::WifiLock").apply {
            setReferenceCounted(false)
        }

        mediaSession = MediaSessionCompat(this, "MySpotifyMediaSession").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    onControlAction?.invoke(ACTION_PLAY)
                }

                override fun onPause() {
                    onControlAction?.invoke(ACTION_PAUSE)
                }

                override fun onSkipToNext() {
                    onControlAction?.invoke(ACTION_NEXT)
                }

                override fun onSkipToPrevious() {
                    onControlAction?.invoke(ACTION_PREV)
                }
            })
        }

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> onControlAction?.invoke(ACTION_PLAY_PAUSE)
            ACTION_NEXT -> onControlAction?.invoke(ACTION_NEXT)
            ACTION_PREV -> onControlAction?.invoke(ACTION_PREV)
        }
        return START_STICKY
    }

    fun updatePlayback(playing: Boolean, title: String, artist: String, coverUrl: String) {
        val titleChanged = title.isNotEmpty() && title != currentTitle
        val artistChanged = artist.isNotEmpty() && artist != currentArtist
        val coverChanged = coverUrl.isNotEmpty() && coverUrl != currentCoverUrl
        val playStateChanged = playing != isPlaying

        if (title.isNotEmpty()) currentTitle = title
        if (artist.isNotEmpty()) currentArtist = artist
        isPlaying = playing

        if (playing) {
            if (wakeLock?.isHeld == false) wakeLock?.acquire(12 * 60 * 60 * 1000L)
            if (wifiLock?.isHeld == false) wifiLock?.acquire()
        } else {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }

        updateMediaSessionState()

        if (coverChanged) {
            currentCoverUrl = coverUrl
            thread {
                try {
                    val url = URL(coverUrl)
                    val bitmap = BitmapFactory.decodeStream(url.openStream())
                    currentCoverBitmap = bitmap
                    updateMediaSessionMetadata()
                    val notification = buildNotification()
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    currentCoverBitmap = null
                }
            }
        } else if (titleChanged || artistChanged || playStateChanged) {
            updateMediaSessionMetadata()
            val notification = buildNotification()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun updateMediaSessionState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
        )
    }

    private fun updateMediaSessionMetadata() {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)

        if (currentCoverBitmap != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentCoverBitmap)
        }

        mediaSession.setMetadata(builder.build())
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this, 3,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
            .addAction(playIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        if (currentCoverBitmap != null) {
            builder.setLargeIcon(currentCoverBitmap)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "myspotify_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_PLAY_PAUSE = "ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_PREV = "ACTION_PREV"
    }
}
