package com.sconcept.mirrordash.provisioning

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Reads the provisioning file from this app's own external-files directory - no runtime
 * permission needed, reachable via `adb push`/`adb pull` without root even where a file-manager
 * app can no longer browse the app's Android/data folder directly (API 30+). Never throws: a
 * missing or malformed file just means "nothing to provision," logged for whoever's setting the
 * unit up.
 */
object ProvisioningConfigLoader {
    private const val TAG = "ProvisioningConfig"
    const val FILE_NAME = "mirrordash_config.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun configFile(context: Context): File = File(context.getExternalFilesDir(null), FILE_NAME)

    fun load(context: Context): ProvisioningConfig? {
        val file = configFile(context)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<ProvisioningConfig>(file.readText())
        }.onFailure { error ->
            Log.w(TAG, "Failed to parse ${file.absolutePath}: ${error.message}")
        }.getOrNull()
    }
}
