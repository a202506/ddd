package com.buzzingmountain.dingclock.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.buzzingmountain.dingclock.core.Constants

/**
 * Thin wrapper around [EncryptedSharedPreferences] using an AES256 master key from AndroidKeyStore.
 * All values on disk are transparently encrypted; we still put an extra AES-GCM layer on sensitive
 * fields like the DingTalk password via [KeystoreManager] for defense in depth.
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences = create(context)

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String): String? = prefs.getString(key, null)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        fun create(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                Constants.SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
