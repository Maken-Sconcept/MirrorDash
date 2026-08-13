package com.sconcept.mirrordash.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the SMB password at rest using a key generated inside the Android Keystore
 * (hardware-backed where available). The key material never leaves the keystore; only
 * ciphertext + IV is persisted in SharedPreferences. Ported unchanged from BerthierOptions -
 * no Fossify or other app-specific dependency here.
 */
class SecureCredentialStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "secure_credentials"
        private const val KEY_ALIAS = "mirrordash_smb_credentials_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val PREF_SMB_PASSWORD = "smb_password_enc"
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? {
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: Exception) {
            // key was invalidated (e.g. device credentials changed) or data is corrupt - treat as absent
            null
        }
    }

    fun saveCredentials(password: String) {
        prefs.edit().putString(PREF_SMB_PASSWORD, encrypt(password)).apply()
    }

    fun loadCredentials(): String? {
        val encoded = prefs.getString(PREF_SMB_PASSWORD, null) ?: return null
        return decrypt(encoded)
    }

    fun hasCredentials() = prefs.contains(PREF_SMB_PASSWORD)

    fun deleteCredentials() {
        prefs.edit().remove(PREF_SMB_PASSWORD).apply()
    }
}

/** Holds the SMB password in memory only, for the lifetime of the process, when the user has
 * not opted to persist it ("Remember connection" disabled). */
object SessionCredentialHolder {
    var smbPassword: String? = null
}
