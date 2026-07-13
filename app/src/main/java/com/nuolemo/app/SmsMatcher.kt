package com.nuolemo.app

import java.util.Locale

object SmsMatcher {
    private const val MIN_PLATE_TOKEN_LENGTH = 7

    @Suppress("UNUSED_PARAMETER")
    fun matches(settings: AppSettings, sender: String?, body: String): Boolean {
        val normalizedBody = body.trim()
        if (normalizedBody.isEmpty()) {
            return false
        }

        val keywordPool = SettingsStore.activeKeywords(settings.keywords)
        if (keywordPool.any { normalizedBody.contains(it, ignoreCase = true) }) {
            return true
        }

        val compactBody = normalizePlateText(normalizedBody)
        val compactPlates =
            settings.plateNumbers
                .map(::normalizePlate)
                .filter { it.length >= MIN_PLATE_TOKEN_LENGTH }
        if (compactPlates.any { compactBody.contains(it) }) {
            return true
        }
        return false
    }

    internal fun normalizePlate(rawValue: String): String {
        return rawValue
            .uppercase(Locale.ROOT)
            .filterNot {
                it.isWhitespace() ||
                    it == '-' ||
                    it == ':' ||
                    it == '：' ||
                    it == '·' ||
                    it == '•' ||
                    it == '.'
            }
    }

    private fun normalizePlateText(body: String): String = normalizePlate(body)
}
