package dev.hien.bitstream

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.hien.bitstream.core.model.PlaybackStatus
import dev.hien.bitstream.core.playback.engine.PlaybackController
import dev.hien.bitstream.core.playback.session.BitstreamPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground [MediaSessionService] hosting [BitstreamPlayer], audio focus
 * (`AUDIOFOCUS_GAIN`, pause on loss, never duck), and a partial wakelock held
 * only while the USB audio stream is active.
 */
class PlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var controller: PlaybackController
    private var mediaSession: MediaSession? = null
    private var player: BitstreamPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false
    private var resumeOnFocusGain: Boolean = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                hasAudioFocus = false
                controller.pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                val wasPlaying = controller.state.value.status == PlaybackStatus.READY ||
                    controller.state.value.status == PlaybackStatus.OPENING
                resumeOnFocusGain = wasPlaying
                if (wasPlaying) {
                    controller.pause()
                }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    controller.play()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val container = (application as BitstreamApp).container
        controller = container.playbackEngine
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "bitstream:playback",
        )?.apply {
            setReferenceCounted(false)
        }

        val bitstreamPlayer = BitstreamPlayer(controller, Looper.getMainLooper())
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        player = bitstreamPlayer
        val session = MediaSession.Builder(this, bitstreamPlayer)
            .setSessionActivity(sessionActivity)
            .build()
        mediaSession = session
        addSession(session)

        serviceScope.launch {
            controller.state.collect { state ->
                when (state.status) {
                    PlaybackStatus.OPENING,
                    PlaybackStatus.READY,
                    -> {
                        acquireAudioFocus()
                        acquireWakeLock()
                    }

                    PlaybackStatus.PAUSED -> {
                        releaseWakeLock()
                    }

                    PlaybackStatus.IDLE,
                    PlaybackStatus.ENDED,
                    PlaybackStatus.ERROR,
                    PlaybackStatus.NO_DAC,
                    -> {
                        releaseWakeLock()
                        if (!resumeOnFocusGain) {
                            abandonAudioFocus()
                        }
                    }
                }
            }
        }

        serviceScope.launch {
            var lastNoticeCount = controller.notices.value.size
            controller.notices.collect { notices ->
                if (notices.size > lastNoticeCount) {
                    val latest = notices.lastOrNull()
                    if (latest != null &&
                        (latest.contains("DAC disconnected", ignoreCase = true) ||
                            latest.contains("USB DAC connected", ignoreCase = true))
                    ) {
                        postDacStatusNotification(
                            message = latest,
                            offerResume = latest.contains("USB DAC connected", ignoreCase = true) &&
                                controller.state.value.status == PlaybackStatus.PAUSED,
                        )
                    }
                }
                lastNoticeCount = notices.size
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESUME_PLAYBACK && ::controller.isInitialized) {
            controller.play()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = player
        val isStreaming = currentPlayer != null &&
            currentPlayer.playWhenReady &&
            (currentPlayer.playbackState == Player.STATE_READY ||
                currentPlayer.playbackState == Player.STATE_BUFFERING)
        if (!isStreaming) {
            currentPlayer?.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        isRunning = false
        releaseWakeLock()
        abandonAudioFocus()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private fun acquireAudioFocus() {
        if (hasAudioFocus) return
        val manager = audioManager ?: return
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
            .also { focusRequest = it }
        val granted = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        hasAudioFocus = granted
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        val request = focusRequest ?: return
        manager.abandonAudioFocusRequest(request)
        hasAudioFocus = false
        resumeOnFocusGain = false
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: return
        if (!lock.isHeld) {
            lock.acquire(MAX_WAKELOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            lock.release()
        }
    }

    private fun postDacStatusNotification(message: String, offerResume: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DAC_CHANNEL_ID,
                "USB DAC Status",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager.createNotificationChannel(channel)
        }
        val openAppIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, DAC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_library_music)
            .setContentTitle("Bitstream USB DAC")
            .setContentText(message)
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
        if (offerResume) {
            val resumePendingIntent = PendingIntent.getService(
                this,
                101,
                Intent(this, PlaybackService::class.java).apply {
                    action = ACTION_RESUME_PLAYBACK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Resume", resumePendingIntent)
        }
        runCatching {
            notificationManager.notify(DAC_NOTIFICATION_ID, builder.build())
        }
    }

    companion object {
        private const val MAX_WAKELOCK_MS = 4L * 60L * 60L * 1_000L
        private const val DAC_CHANNEL_ID = "bitstream_dac_status"
        private const val DAC_NOTIFICATION_ID = 4201
        const val ACTION_RESUME_PLAYBACK = "dev.hien.bitstream.action.RESUME"

        @Volatile
        private var isRunning: Boolean = false

        fun ensureStarted(context: Context) {
            if (isRunning) return
            val appContext = context.applicationContext
            val intent = Intent(appContext, PlaybackService::class.java)
            runCatching {
                appContext.startService(intent)
            }.onFailure {
                runCatching { ContextCompat.startForegroundService(appContext, intent) }
            }
        }
    }
}
