package com.nuolemo.app

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class AlarmService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        AlarmNotifier.ensureAlarmChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarmAndSelf(fromPlaybackCallback = false)
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                val sender = intent?.getStringExtra(EXTRA_SENDER)
                val body =
                    intent?.getStringExtra(EXTRA_SMS_BODY)
                        ?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.test_sms_body)

                startForeground(
                    AlarmNotifier.NOTIFICATION_ID,
                    AlarmNotifier.buildForegroundNotification(this, sender, body),
                )

                AlarmPlaybackController.start(
                    context = this,
                    settings = SettingsStore.load(this),
                    sender = sender,
                    body = body,
                    owner = AlarmPlaybackController.Owner.SERVICE,
                    onStopped = { stopAlarmAndSelf(fromPlaybackCallback = true) },
                )
                return START_NOT_STICKY
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AlarmPlaybackController.stopIfOwnedBy(
            owner = AlarmPlaybackController.Owner.SERVICE,
            notifyListener = false,
        )
        stopForegroundCompat()
        AlarmNotifier.cancelAlarmNotification(this)
        running = false
        super.onDestroy()
    }

    private fun stopAlarmAndSelf(fromPlaybackCallback: Boolean) {
        if (!fromPlaybackCallback) {
            AlarmPlaybackController.stopIfOwnedBy(AlarmPlaybackController.Owner.SERVICE)
        }
        stopForegroundCompat()
        AlarmNotifier.cancelAlarmNotification(this)
        stopSelf()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val ACTION_START = "com.nuolemo.app.action.START_ALARM"
        const val ACTION_STOP = "com.nuolemo.app.action.STOP_ALARM"
        const val EXTRA_SMS_BODY = "extra_sms_body"
        const val EXTRA_SENDER = "extra_sender"

        @Volatile
        private var running = false

        fun isRunning(): Boolean = running
    }
}
