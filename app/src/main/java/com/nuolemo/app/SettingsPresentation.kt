package com.nuolemo.app

import android.content.Context

object SettingsPresentation {
    fun durationLabel(context: Context, seconds: Int): String {
        return when (seconds) {
            30 -> context.getString(R.string.duration_30)
            60 -> context.getString(R.string.duration_60)
            120 -> context.getString(R.string.duration_120)
            0 -> context.getString(R.string.duration_manual)
            else -> context.getString(R.string.duration_60)
        }
    }

    fun rulesOverview(context: Context, settings: AppSettings): String {
        val keywordCount = SettingsStore.activeKeywords(settings.keywords).size
        return if (settings.plateNumbers.isEmpty()) {
            context.getString(
                R.string.home_rules_overview_no_plate,
                keywordCount,
            )
        } else {
            context.getString(
                R.string.home_rules_overview,
                keywordCount,
                settings.plateNumbers.size,
            )
        }
    }

    fun alarmOverview(context: Context, settings: AppSettings): String {
        val vibration =
            if (settings.vibrate) {
                context.getString(R.string.summary_option_on)
            } else {
                context.getString(R.string.summary_option_off)
            }

        return context.getString(
            R.string.home_alarm_overview,
            durationLabel(context, settings.alarmDurationSeconds),
            vibration,
            context.getString(R.string.summary_alarm_sound_system),
        )
    }
}
