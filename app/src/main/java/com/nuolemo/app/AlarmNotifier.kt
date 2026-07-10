package com.nuolemo.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object AlarmNotifier {
    const val CHANNEL_ID = "move_car_alarm"
    const val NOTIFICATION_ID = 1107

    private const val REQUEST_CODE_ALARM_ACTIVITY = 2000

    fun ensureAlarmChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.alarm_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
        manager.createNotificationChannel(channel)
    }

    fun buildForegroundNotification(context: Context, sender: String?, body: String): Notification {
        ensureAlarmChannel(context)
        return baseBuilder(
            context = context,
            sender = sender,
            body = body,
            enableFullScreenIntent = false,
            ongoing = true,
        ).build()
    }

    @SuppressLint("MissingPermission")
    fun showUrgentAlarmNotification(context: Context, sender: String?, body: String) {
        ensureAlarmChannel(context)
        if (!areNotificationsEnabled(context)) {
            return
        }

        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            baseBuilder(
                context = context,
                sender = sender,
                body = body,
                enableFullScreenIntent = true,
                ongoing = true,
            ).build(),
        )
    }

    fun cancelAlarmNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        val managerCompat = NotificationManagerCompat.from(context)
        val permissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return permissionGranted && managerCompat.areNotificationsEnabled()
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (!areNotificationsEnabled(context)) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
        } else {
            true
        }
    }

    fun createAlarmActivityIntent(context: Context, sender: String?, body: String): Intent {
        return Intent(context, AlarmActivity::class.java).apply {
            putExtra(AlarmService.EXTRA_SENDER, sender)
            putExtra(AlarmService.EXTRA_SMS_BODY, body)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
    }

    private fun baseBuilder(
        context: Context,
        sender: String?,
        body: String,
        enableFullScreenIntent: Boolean,
        ongoing: Boolean,
    ): NotificationCompat.Builder {
        val contentText = buildContentText(context, sender, body)
        val alarmIntent = createAlarmActivityIntent(context, sender, body)
        val alarmPendingIntent = createAlarmPendingIntent(context, alarmIntent)
        val stopPendingIntent = AlarmStopReceiver.createPendingIntent(context)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(alarmPendingIntent)
            .addAction(R.drawable.ic_notification, context.getString(R.string.stop_alarm), stopPendingIntent)
            .apply {
                if (enableFullScreenIntent) {
                    setFullScreenIntent(alarmPendingIntent, true)
                }
            }
    }

    private fun createAlarmPendingIntent(context: Context, intent: Intent): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, REQUEST_CODE_ALARM_ACTIVITY, intent, flags)
    }

    private fun buildContentText(context: Context, sender: String?, body: String): String {
        val summary = body.trim().replace('\n', ' ').take(80)
        val senderLabel = sender?.takeIf { it.isNotBlank() } ?: context.getString(R.string.test_sender)
        return "$senderLabel · $summary"
    }
}
