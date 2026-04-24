package com.buzzingmountain.dingclock.data

data class AppConfig(
    /** base64(iv || ciphertext) from [com.buzzingmountain.dingclock.crypto.KeystoreManager]. */
    val passwordCipher: String = "",
) {
    fun isComplete(): Boolean = passwordCipher.isNotBlank()
}
