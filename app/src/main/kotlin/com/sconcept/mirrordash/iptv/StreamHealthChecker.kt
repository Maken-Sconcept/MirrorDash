package com.sconcept.mirrordash.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Best-effort, low-cost reachability probe for a Movies/Series item's stream - not run for live
 * TV (see [IptvViewModel]'s doc comment on why Movies/Series only: a live channel list can run
 * into the thousands, a VOD/series category realistically doesn't).
 *
 * Cost is bounded two ways rather than by scanning a whole catalog up front: [permits] caps how
 * many checks are ever in flight at once regardless of how many [VodItemCard]s happen to be
 * visible simultaneously, and callers only request a check for an item once it's actually
 * scrolled into view (`LazyVerticalGrid` only composes near-viewport items, so this falls out of
 * the UI for free - see `IptvVodScreen.kt`).
 */
object StreamHealthChecker {
    private val permits = Semaphore(MAX_CONCURRENT_CHECKS)

    /** Resolves [item]'s actual stream URL first (a `create_link` portal round-trip - the same
     * cost [IptvViewModel.playVodItem] pays to actually play it), then a short HEAD/ranged-GET
     * probe against that URL. A failure at either step is [StreamHealth.OFFLINE] - from the
     * user's point of view "the portal won't resolve this" and "the resolved link doesn't answer"
     * are the same broken-link symptom. */
    suspend fun check(client: StalkerPortalClient, item: StalkerVodItem): StreamHealth = permits.withPermit {
        val streamUrl = client.resolveVodStreamUrl(item).getOrNull() ?: return@withPermit StreamHealth.OFFLINE
        if (probe(streamUrl, method = "HEAD")) return@withPermit StreamHealth.ONLINE
        if (probe(streamUrl, method = "GET", rangeZeroByte = true)) return@withPermit StreamHealth.ONLINE
        StreamHealth.OFFLINE
    }

    private suspend fun probe(url: String, method: String, rangeZeroByte: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    if (rangeZeroByte) setRequestProperty("Range", "bytes=0-0")
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 4 rev: 250 Safari/533.3",
                    )
                }
                connection.responseCode in 200..399
            } catch (e: Exception) {
                false
            } finally {
                connection?.disconnect()
            }
        }

    private const val MAX_CONCURRENT_CHECKS = 2
    private const val TIMEOUT_MS = 4_000
}
