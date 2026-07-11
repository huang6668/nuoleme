package com.nuolemo.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsMatcherTest {
    @Test
    fun `matches strict chinese move car keyword`() {
        val matched =
            SmsMatcher.matches(
                settings = sampleSettings(),
                sender = "1069",
                body = "您的车辆挡住了别人，请及时挪车。",
            )

        assertTrue(matched)
    }

    @Test
    fun `matches move car wording without trusted sender`() {
        val matched =
            SmsMatcher.matches(
                settings = sampleSettings(),
                sender = "10086",
                body = "请车主移车，出口被您挡住了。",
            )

        assertTrue(matched)
    }

    @Test
    fun `matches hubei traffic police real sms`() {
        val matched =
            SmsMatcher.matches(
                settings = sampleSettings(plateNumbers = listOf("鄂W8608J")),
                sender = "湖北交警",
                body = "【湖北交警】您的小型汽车鄂W8608J于2026年7月9日7时19分在流芳路高新四路至高新六路未按规定停放已被记录，请立即驶离，未及时驶离的，将依法予以处罚。",
            )

        assertTrue(matched)
    }

    @Test
    fun `does not match verification code text`() {
        val matched =
            SmsMatcher.matches(
                settings = sampleSettings(),
                sender = "1069",
                body = "验证码 123456，五分钟内有效。",
            )

        assertFalse(matched)
    }

    @Test
    fun `matches normalized plate number`() {
        val matched =
            SmsMatcher.matches(
                settings = sampleSettings(plateNumbers = listOf("鄂A12345")),
                sender = "交管平台",
                body = "鄂 A12345 请挪车，车辆挡道。",
            )

        assertTrue(matched)
    }

    @Test
    fun `matches when custom keywords configured`() {
        val matched =
            SmsMatcher.matches(
                settings = sampleSettings(keywords = listOf("地库堵门")),
                sender = "物业",
                body = "地库堵门，请联系车主尽快处理。",
            )

        assertTrue(matched)
    }

    @Test
    fun `matches trusted sender marker with strict default keyword`() {
        val matched =
            SmsMatcher.matches(
                settings = sampleSettings(keywords = emptyList()),
                sender = "交管12123",
                body = "您的车辆妨碍通行，请立即驶离。",
            )

        assertTrue(matched)
    }

    @Test
    fun `does not overmatch generic car owner text`() {
        val matched =
            SmsMatcher.matches(
                settings = sampleSettings(keywords = emptyList()),
                sender = "银行",
                body = "尊敬的车主，您的积分即将过期。",
            )

        assertFalse(matched)
    }

    @Test
    fun `empty keyword list still allows plate matching`() {
        val matched =
            SmsMatcher.matches(
                settings = sampleSettings(keywords = emptyList(), plateNumbers = listOf("沪B-88888")),
                sender = null,
                body = "沪 B88888 请及时移车，谢谢。",
            )

        assertTrue(matched)
    }

    private fun sampleSettings(
        keywords: List<String> = SettingsStore.defaultKeywords,
        plateNumbers: List<String> = emptyList(),
    ): AppSettings {
        return AppSettings(
            enabled = true,
            keywords = keywords,
            plateNumbers = plateNumbers,
            alarmDurationSeconds = 60,
            vibrate = true,
        )
    }
}
