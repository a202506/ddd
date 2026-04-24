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
    fun `config with password is complete`() {
        val cfg = AppConfig(passwordCipher = "ZmFrZWNpcGhlcg==")
        assertTrue(cfg.isComplete())
    }

    @Test
    fun `config with blank password is incomplete`() {
        val cfg = AppConfig(passwordCipher = "")
        assertFalse(cfg.isComplete())
    }

    @Test
    fun `moshi roundtrip preserves password field`() {
        val original = AppConfig(passwordCipher = "Y2lwaGVy")
        val json = adapter.toJson(original)
        val parsed = adapter.fromJson(json)
        assertNotNull(parsed)
        assertEquals(original, parsed)
    }
}
