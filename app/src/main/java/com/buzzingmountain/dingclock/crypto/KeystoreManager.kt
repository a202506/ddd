package com.buzzingmountain.dingclock.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.buzzingmountain.dingclock.core.Constants
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM wrapper backed by AndroidKeyStore. Produces self-contained base64(iv || ciphertext)
 * strings so encrypted values can live inside JSON.
 */
object KeystoreManager {
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val ks = loadKeyStore()
        (ks.getKey(Constants.KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            Constants.KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        kg.init(spec)
        return kg.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val iv = cipher.iv
        require(iv.size == IV_LEN) { "unexpected IV length: ${iv.size}" }
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(encrypted: String): String {
        val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
        require(bytes.size > IV_LEN) { "ciphertext too short" }
        val iv = bytes.copyOfRange(0, IV_LEN)
        val ct = bytes.copyOfRange(IV_LEN, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(ct).toString(Charsets.UTF_8)
    }

    /** For tests / factory reset. */
    fun deleteKey() {
        runCatching { loadKeyStore().deleteEntry(Constants.KEYSTORE_ALIAS) }
    }
}
