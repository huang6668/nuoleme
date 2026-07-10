package com.nuolemo.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val settings = SettingsStore.load(context)
        if (!settings.enabled) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            return
        }

        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: messages.firstOrNull()?.originatingAddress
        val body =
            messages.joinToString(separator = "") { message ->
                message.displayMessageBody ?: message.messageBody ?: ""
            }.trim()

        if (!SmsMatcher.matches(settings, sender, body)) {
            return
        }

        val serviceIntent =
            Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_START
                putExtra(AlarmService.EXTRA_SMS_BODY, body)
                putExtra(AlarmService.EXTRA_SENDER, sender)
            }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (_: IllegalStateException) {
            AlarmNotifier.showUrgentAlarmNotification(context, sender, body)
        } catch (_: SecurityException) {
            AlarmNotifier.showUrgentAlarmNotification(context, sender, body)
        }
    }
}
