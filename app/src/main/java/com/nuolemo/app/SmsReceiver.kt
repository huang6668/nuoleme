package com.nuolemo.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

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

        AlarmLaunchHelper.startAlarm(context, sender, body)
    }
}
