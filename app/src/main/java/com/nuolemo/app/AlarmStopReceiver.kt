package com.nuolemo.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        stopAlarm(context)
    }

    companion object {
        private const val REQUEST_CODE_STOP = 2002

        fun createPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AlarmStopReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, REQUEST_CODE_STOP, intent, flags)
        }

        fun stopAlarm(context: Context) {
            AlarmPlaybackController.stop()
            AlarmNotifier.cancelAlarmNotification(context)
            runCatching {
                context.startService(
                    Intent(context, AlarmService::class.java).apply {
                        action = AlarmService.ACTION_STOP
                    },
                )
            }
        }
    }
}
