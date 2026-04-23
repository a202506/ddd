package com.buzzingmountain.dingclock.data

enum class HolidayMode { WEEKENDS_ONLY, CUSTOM_LIST }

data class AppConfig(
    val colleagueName: String = "",
    /** base64(iv || ciphertext) from [com.buzzingmountain.dingclock.crypto.KeystoreManager]. */
    val passwordCipher: String = "",
    /** HH:mm, 24h. */
    val morningPunchAt: String = "09:00",
    /** HH:mm, 24h. */
    val eveningPunchAt: String = "18:00",
    val randomJitterSeconds: Int = 90,
    val holidayMode: HolidayMode = HolidayMode.WEEKENDS_ONLY,
    /** yyyy-MM-dd. */
    val customHolidays: Set<String> = emptySet(),
    val webhookUrl: String = "",
    val webhookSecret: String = "",
    val preArmSeconds: Int = 60,
    val postDelaySeconds: Int = 120,
    val enabled: Boolean = true,
) {
    fun isComplete(): Boolean =
        // Password is the only hard requirement — DingTalk remembers the phone number, and
        // the device stays on Wi-Fi (hotspot is flipped externally on another phone).
        passwordCipher.isNotBlank() &&
            morningPunchAt.isNotBlank() &&
            eveningPunchAt.isNotBlank()
}
