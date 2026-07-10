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
        return if (settings.plateNumbers.isEmpty()) {
            context.getString(
                R.string.home_rules_overview_no_plate,
                settings.keywords.size,
            )
        } else {
            context.getString(
                R.string.home_rules_overview,
                settings.keywords.size,
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
        val volume =
            if (settings.maximizeVolume) {
                context.getString(R.string.summary_volume_max)
            } else {
                context.getString(R.string.summary_volume_keep)
            }

        return context.getString(
            R.string.home_alarm_overview,
            durationLabel(context, settings.alarmDurationSeconds),
            vibration,
            volume,
        )
    }
}
