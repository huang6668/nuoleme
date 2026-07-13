package com.nuolemo.app

import android.content.Context
import java.util.Locale

data class AppSettings(
    val enabled: Boolean,
    val keywords: List<String>,
    val plateNumbers: List<String>,
    val alarmDurationSeconds: Int,
    val vibrate: Boolean,
)

object SettingsStore {
    private const val PREFS_NAME = "nuolemo_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_KEYWORDS = "keywords"
    private const val KEY_PLATE_NUMBERS = "plate_numbers"
    private const val KEY_ALARM_DURATION_SECONDS = "alarm_duration_seconds"
    private const val KEY_VIBRATE = "vibrate"
    private const val LEGACY_KEY_MAXIMIZE_VOLUME = "maximize_volume"

    val defaultKeywords: List<String> =
        listOf(
            "挪车",
            "移车",
            "一键挪车",
            "妨碍通行",
            "请立即驶离",
            "请及时驶离",
            "车辆挡道",
            "车辆妨碍",
        )

    fun load(context: Context): AppSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            keywords = sanitizeCustomKeywords(
                prefs.getString(KEY_KEYWORDS, "").orEmpty(),
            ),
            plateNumbers = sanitizePlateNumbers(
                prefs.getString(KEY_PLATE_NUMBERS, "").orEmpty(),
            ),
            alarmDurationSeconds = normalizeDuration(
                prefs.getInt(KEY_ALARM_DURATION_SECONDS, 60),
            ),
            vibrate = prefs.getBoolean(KEY_VIBRATE, true),
        )
    }

    fun save(context: Context, settings: AppSettings) {
        val sanitizedKeywords = sanitizeCustomKeywords(formatEditorInput(settings.keywords))
        val sanitizedPlates = sanitizePlateNumbers(formatEditorInput(settings.plateNumbers))
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_KEYWORDS, formatEditorInput(sanitizedKeywords))
            .putString(KEY_PLATE_NUMBERS, formatEditorInput(sanitizedPlates))
            .putInt(KEY_ALARM_DURATION_SECONDS, normalizeDuration(settings.alarmDurationSeconds))
            .putBoolean(KEY_VIBRATE, settings.vibrate)
            .remove(LEGACY_KEY_MAXIMIZE_VOLUME)
            .apply()
    }

    fun sanitizeKeywords(rawInput: String): List<String> = splitAndClean(rawInput)

    fun activeKeywords(customKeywords: List<String>): List<String> {
        return (defaultKeywords + customKeywords).distinctBy { it.lowercase(Locale.ROOT) }
    }

    fun sanitizePlateNumbers(rawInput: String): List<String> =
        splitAndClean(rawInput).map { it.uppercase(Locale.ROOT) }

    fun formatEditorInput(values: List<String>): String = values.joinToString(separator = "\n")

    private fun sanitizeCustomKeywords(rawInput: String): List<String> {
        return sanitizeKeywords(rawInput).filterNot { keyword ->
            defaultKeywords.any { defaultKeyword -> defaultKeyword.equals(keyword, ignoreCase = true) }
        }
    }

    private fun splitAndClean(rawInput: String): List<String> {
        val seen = linkedSetOf<String>()
        rawInput
            .split("\n", ",", "，", ";", "；")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { seen += it }
        return seen.toList()
    }

    private fun normalizeDuration(rawValue: Int): Int {
        return when (rawValue) {
            0, 30, 60, 120 -> rawValue
            else -> 60
        }
    }
}
