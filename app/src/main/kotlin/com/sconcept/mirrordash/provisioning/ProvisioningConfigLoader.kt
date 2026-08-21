package com.sconcept.mirrordash.provisioning

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads the bundled default first-run configuration and lets an external file override it. The
 * external-files location needs no runtime permission and is reachable with `adb push`/`adb pull`
 * even where a file manager cannot browse Android/data directly (API 30+).
 */
object ProvisioningConfigLoader {
    private const val TAG = "ProvisioningConfig"
    const val FILE_NAME = "mirrordash_config.json"
    private const val BUNDLED_ASSET_PATH = "provisioning/$FILE_NAME"

    private val json = Json { ignoreUnknownKeys = true }

    fun configFile(context: Context): File = File(context.getExternalFilesDir(null), FILE_NAME)

    fun load(context: Context): ProvisioningConfig? {
        val file = configFile(context)
        if (file.exists()) {
            return decode(file.readText(), file.absolutePath)
        }
        return runCatching {
            context.assets.open(BUNDLED_ASSET_PATH).bufferedReader().use { it.readText() }
        }.mapCatching { payload ->
            json.decodeFromString<ProvisioningConfig>(payload)
        }.onFailure { error ->
            Log.w(TAG, "Failed to load bundled provisioning config: ${error.message}")
        }.getOrNull()
    }

    private fun decode(payload: String, source: String): ProvisioningConfig? = runCatching {
        json.decodeFromString<ProvisioningConfig>(payload)
    }.onFailure { error ->
        Log.w(TAG, "Failed to parse $source: ${error.message}")
    }.getOrNull()
}
