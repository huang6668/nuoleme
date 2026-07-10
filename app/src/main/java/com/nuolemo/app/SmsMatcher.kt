package com.nuolemo.app

import java.util.Locale

object SmsMatcher {
    private val strictDefaultKeywords =
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

    private val trustedMarkers =
        listOf(
            "12123",
            "交管12123",
            "公安交管",
        )

    fun matches(settings: AppSettings, sender: String?, body: String): Boolean {
        val normalizedBody = body.trim()
        if (normalizedBody.isEmpty()) {
            return false
        }

        val keywordPool = (SettingsStore.defaultKeywords + settings.keywords).distinct()
        if (keywordPool.any { normalizedBody.contains(it, ignoreCase = true) }) {
            return true
        }

        val compactBody = normalizePlateText(normalizedBody)
        val compactPlates = settings.plateNumbers.map(::normalizePlate).filter { it.isNotEmpty() }
        if (compactPlates.any { compactBody.contains(it) }) {
            return true
        }

        val trustedSourceText = "${sender.orEmpty()}\n$normalizedBody"
        val trustedSource = trustedMarkers.any { trustedSourceText.contains(it, ignoreCase = true) }
        return if (trustedSource) {
            strictDefaultKeywords.any { normalizedBody.contains(it, ignoreCase = true) }
        } else {
            strictDefaultKeywords.any { normalizedBody.contains(it, ignoreCase = true) }
        }
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
