package com.buzzingmountain.dingclock.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(AppConfig::class.java)

    @Test
    fun `default config is incomplete`() {
        assertFalse(AppConfig().isComplete())
    }

    @Test
    fun `full config is complete`() {
        val cfg = AppConfig(
            phoneNumber = "13812345678",
            passwordCipher = "ZmFrZWNpcGhlcg==",
            wifiSsid = "OfficeWiFi",
        )
        assertTrue(cfg.isComplete())
    }

    @Test
    fun `masked phone keeps prefix and suffix`() {
        val cfg = AppConfig(phoneNumber = "13812345678")
        assertEquals("138****5678", cfg.maskedPhone())
    }

    @Test
    fun `masked phone handles short numbers`() {
        assertEquals("****", AppConfig(phoneNumber = "1234").maskedPhone())
        assertEquals("(未配置)", AppConfig(phoneNumber = "").maskedPhone())
    }

    @Test
    fun `moshi roundtrip preserves all fields`() {
        val original = AppConfig(
            phoneNumber = "13912345678",
            colleagueName = "张三",
            passwordCipher = "Y2lwaGVy",
            wifiSsid = "OfficeWiFi",
            morningPunchAt = "08:30",
            eveningPunchAt = "18:30",
            randomJitterSeconds = 60,
            holidayMode = HolidayMode.CUSTOM_LIST,
            customHolidays = setOf("2026-05-01", "2026-10-01"),
            webhookUrl = "https://oapi.dingtalk.com/robot/send?access_token=x",
            webhookSecret = "SECabc",
            preArmSeconds = 30,
            postDelaySeconds = 90,
            enabled = false,
        )
        val json = adapter.toJson(original)
        val parsed = adapter.fromJson(json)
        assertNotNull(parsed)
        assertEquals(original, parsed)
    }
}
