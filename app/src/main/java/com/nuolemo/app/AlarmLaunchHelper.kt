package com.nuolemo.app

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

enum class AlarmLaunchResult {
    FOREGROUND_SERVICE_STARTED,
    NOTIFICATION_FALLBACK,
}

object AlarmLaunchHelper {
    fun startAlarm(context: Context, sender: String?, body: String): AlarmLaunchResult {
        val serviceIntent =
            Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_START
                putExtra(AlarmService.EXTRA_SMS_BODY, body)
                putExtra(AlarmService.EXTRA_SENDER, sender)
            }

        return try {
            ContextCompat.startForegroundService(context, serviceIntent)
            AlarmLaunchResult.FOREGROUND_SERVICE_STARTED
        } catch (_: IllegalStateException) {
            AlarmNotifier.showUrgentAlarmNotification(context, sender, body)
            AlarmLaunchResult.NOTIFICATION_FALLBACK
        } catch (_: SecurityException) {
            AlarmNotifier.showUrgentAlarmNotification(context, sender, body)
            AlarmLaunchResult.NOTIFICATION_FALLBACK
        }
    }
}
