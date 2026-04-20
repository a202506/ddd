package com.buzzingmountain.dingclock.data

import android.content.Context
import com.buzzingmountain.dingclock.crypto.KeystoreManager
import com.buzzingmountain.dingclock.crypto.SecurePrefs
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import timber.log.Timber

class ConfigRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs by lazy { SecurePrefs(appContext) }
    private val adapter by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(AppConfig::class.java)
    }

    fun load(): AppConfig? {
        val json = prefs.getString(KEY_CONFIG) ?: return null
        return runCatching { adapter.fromJson(json) }
            .onFailure { Timber.e(it, "Failed to parse AppConfig JSON") }
            .getOrNull()
    }

    fun save(config: AppConfig) {
        prefs.putString(KEY_CONFIG, adapter.toJson(config))
    }

    fun clear() {
        prefs.remove(KEY_CONFIG)
    }

    fun hasConfig(): Boolean {
        val cfg = load() ?: return false
        return cfg.isComplete()
    }

    /** Encrypts a plaintext password into a base64(iv||ct) string suitable for AppConfig.passwordCipher. */
    fun encryptPassword(plain: String): String = KeystoreManager.encrypt(plain)

    /** Decrypts a stored passwordCipher; returns null on failure. Caller should zero out the result ASAP. */
    fun decryptPassword(cfg: AppConfig): String? {
        if (cfg.passwordCipher.isBlank()) return null
        return runCatching { KeystoreManager.decrypt(cfg.passwordCipher) }
            .onFailure { Timber.e(it, "Failed to decrypt password") }
            .getOrNull()
    }

    companion object {
        private const val KEY_CONFIG = "config_v1"
    }
}
