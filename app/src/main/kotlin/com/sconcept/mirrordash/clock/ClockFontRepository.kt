package com.sconcept.mirrordash.clock

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val GOOGLE_FONT_CATALOG_ASSET = "google_font_catalog.json"

class ClockFontRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadCatalog(): List<GoogleFontCatalogEntry> = withContext(Dispatchers.IO) {
        val raw = appContext.assets.open(GOOGLE_FONT_CATALOG_ASSET).bufferedReader().use { it.readText() }
        json.decodeFromString(raw)
    }

    suspend fun download(entry: GoogleFontCatalogEntry): DownloadedClockFont = withContext(Dispatchers.IO) {
        val apiUrl = "https://api.github.com/repos/google/fonts/contents/${entry.preferredPath}?ref=main"
        val metadata = httpGet(apiUrl, accept = "application/vnd.github+json")
        val downloadUrl = JSONObject(metadata).optString("download_url")
            .takeIf { it.isNotBlank() }
            ?: error("Couldn't resolve a download URL for ${entry.family}.")

        val fontDir = File(appContext.filesDir, "clock-fonts").apply { mkdirs() }
        val extension = entry.preferredPath.substringAfterLast('.', "ttf")
        val target = File(fontDir, "${entry.id.removePrefix(CLOCK_FONT_GOOGLE_PREFIX)}.$extension")
        val temp = File(fontDir, "${target.name}.download")

        URL(downloadUrl).openConnection().let { connection ->
            require(connection is HttpURLConnection) { "Unexpected connection type." }
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "MirrorDash")
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Download failed (${connection.responseCode}).")
            }
            temp.outputStream().use { output ->
                connection.inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()
        }

        runCatching { ClockFontLibrary.typeface(appContext, entry.id, listOf(DownloadedClockFont(entry.id, entry.family, entry.category, entry.license, temp.absolutePath, entry.preferredPath, System.currentTimeMillis()))) }
            .getOrElse {
                temp.delete()
                throw IllegalStateException("Downloaded font file for ${entry.family} could not be opened.", it)
            }

        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }

        DownloadedClockFont(
            id = entry.id,
            family = entry.family,
            category = entry.category,
            license = entry.license,
            localPath = target.absolutePath,
            sourcePath = entry.preferredPath,
            downloadedAtEpochMs = System.currentTimeMillis(),
        )
    }

    suspend fun seedStarterPack(): List<DownloadedClockFont> = withContext(Dispatchers.IO) {
        val fontDir = File(appContext.filesDir, "clock-fonts").apply { mkdirs() }
        ClockFontLibrary.starterPack.map { bundled ->
            val target = File(fontDir, "${bundled.id.removePrefix(CLOCK_FONT_GOOGLE_PREFIX)}.ttf")
            if (!target.exists()) {
                appContext.assets.open(bundled.assetPath).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            DownloadedClockFont(
                id = bundled.id,
                family = bundled.family,
                category = bundled.category,
                license = bundled.license,
                localPath = target.absolutePath,
                sourcePath = bundled.assetPath,
                downloadedAtEpochMs = target.lastModified(),
            )
        }
    }

    fun delete(downloadedFont: DownloadedClockFont) {
        if (downloadedFont.sourcePath.startsWith("clock-fonts/")) return
        File(downloadedFont.localPath).delete()
    }

    private fun httpGet(url: String, accept: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "MirrorDash")
        connection.setRequestProperty("Accept", accept)
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(body.ifBlank { "Request failed (${connection.responseCode})." })
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}
