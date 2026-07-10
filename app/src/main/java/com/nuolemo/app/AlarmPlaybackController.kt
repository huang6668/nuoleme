package com.nuolemo.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object AlarmPlaybackController {
    private const val SAFETY_MAX_DURATION_SECONDS = 300
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    enum class Owner {
        SERVICE,
        ACTIVITY,
    }

    @Volatile
    var currentOwner: Owner? = null
        private set

    @Volatile
    var lastSender: String? = null
        private set

    @Volatile
    var lastBody: String = ""
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var timeoutRunnable: Runnable? = null
    private var onStoppedListener: (() -> Unit)? = null

    fun start(
        context: Context,
        settings: AppSettings,
        sender: String?,
        body: String,
        owner: Owner,
        onStopped: (() -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        var previousListener: (() -> Unit)? = null

        synchronized(lock) {
            lastSender = sender
            lastBody = body

            val alreadyActive = currentOwner == owner && (mediaPlayer != null || vibrator != null)
            if (alreadyActive) {
                onStoppedListener = onStopped
                if (settings.vibrate && vibrator == null) {
                    startVibrationLocked(appContext)
                } else if (!settings.vibrate) {
                    cancelVibrationLocked()
                }
                scheduleStopLocked(settings.alarmDurationSeconds)
                return
            }

            previousListener = stopLocked(notifyListener = currentOwner != null && currentOwner != owner)
            currentOwner = owner
            onStoppedListener = onStopped

            mediaPlayer = createAlarmPlayer(appContext).apply {
                this?.start()
            }

            if (settings.vibrate) {
                startVibrationLocked(appContext)
            }

            scheduleStopLocked(settings.alarmDurationSeconds)
        }

        previousListener?.invoke()
    }

    fun stop() {
        stopInternal(owner = null, notifyListener = true)
    }

    fun stopIfOwnedBy(owner: Owner, notifyListener: Boolean = true) {
        stopInternal(owner = owner, notifyListener = notifyListener)
    }

    fun isRunning(): Boolean = currentOwner != null

    private fun stopInternal(owner: Owner?, notifyListener: Boolean) {
        val listener =
            synchronized(lock) {
                if (owner != null && currentOwner != owner) {
                    null
                } else {
                    stopLocked(notifyListener)
                }
            }

        listener?.invoke()
    }

    private fun stopLocked(notifyListener: Boolean): (() -> Unit)? {
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null

        mediaPlayer?.runCatching {
            if (isPlaying) {
                stop()
            }
        }
        mediaPlayer?.release()
        mediaPlayer = null

        cancelVibrationLocked()

        currentOwner = null
        lastSender = null
        lastBody = ""

        val listener = if (notifyListener) onStoppedListener else null
        onStoppedListener = null
        return listener
    }

    private fun scheduleStopLocked(durationSeconds: Int) {
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        val effectiveSeconds = if (durationSeconds > 0) durationSeconds else SAFETY_MAX_DURATION_SECONDS
        timeoutRunnable = Runnable { stop() }
        mainHandler.postDelayed(timeoutRunnable!!, effectiveSeconds * 1_000L)
    }

    private fun createAlarmPlayer(context: Context): MediaPlayer? {
        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

        resolveAlarmUris().forEach { uri ->
            val player =
                runCatching {
                    MediaPlayer().apply {
                        setAudioAttributes(audioAttributes)
                        isLooping = true
                        setDataSource(context, uri)
                        prepare()
                    }
                }.getOrNull()
            if (player != null) {
                return player
            }
        }

        return null
    }

    private fun resolveAlarmUris(): List<Uri> {
        return buildList {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let(::add)
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let(::add)
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let(::add)
        }.distinct()
    }

    private fun startVibrationLocked(context: Context) {
        val resolvedVibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

        if (resolvedVibrator?.hasVibrator() != true) {
            vibrator = null
            return
        }

        vibrator = resolvedVibrator
        val pattern = longArrayOf(0, 450, 180, 450, 180, 600)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun cancelVibrationLocked() {
        vibrator?.cancel()
        vibrator = null
    }
}
