package com.sconcept.mirrordash.wedding

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class WeddingPinResult {
    SUCCESS,
    INVALID_FORMAT,
    INCORRECT,
    STORAGE_ERROR,
}

object WeddingPinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun sanitize(value: String): String = value.filter(Char::isDigit).take(MAX_LENGTH)

    fun isValid(value: String): Boolean =
        value.length in MIN_LENGTH..MAX_LENGTH && value.all(Char::isDigit)
}

/** Keeps the host PIN out of DataStore and protects it with an Android Keystore AES key. */
class WeddingPinStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.contains(PREF_PIN)

    fun save(pin: String) {
        require(WeddingPinPolicy.isValid(pin))
        prefs.edit().putString(PREF_PIN, encrypt(pin)).apply()
    }

    fun matches(pin: String): Boolean {
        if (!WeddingPinPolicy.isValid(pin)) return false
        val encoded = prefs.getString(PREF_PIN, null) ?: return false
        val stored = decrypt(encoded) ?: return false
        return MessageDigest.isEqual(
            stored.toByteArray(Charsets.UTF_8),
            pin.toByteArray(Charsets.UTF_8),
        )
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun encrypt(pin: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        if (encrypted.size <= GCM_IV_LENGTH_BYTES) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, encrypted.copyOfRange(0, GCM_IV_LENGTH_BYTES)),
        )
        String(cipher.doFinal(encrypted.copyOfRange(GCM_IV_LENGTH_BYTES, encrypted.size)), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val PREFS_NAME = "wedding_mode_credentials"
        const val PREF_PIN = "host_pin"
        const val KEY_ALIAS = "mirrordash_wedding_mode_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_IV_LENGTH_BYTES = 12
    }
}
