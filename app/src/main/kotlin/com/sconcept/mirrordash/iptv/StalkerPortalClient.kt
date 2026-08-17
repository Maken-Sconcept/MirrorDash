package com.sconcept.mirrordash.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal client for the Stalker/Ministra "portal" protocol that STBEMU Pro emulates: an
 * MAG-series set-top box authenticates by MAC address alone (no username/password) against a
 * `portal.php`-style endpoint, trades a handshake for a bearer token, then drives everything else
 * (channel list, stream resolution) through that same endpoint with `type`/`action` query params.
 * Protocol shape confirmed against STBEMU Pro 1.1.7's decompiled action list (`get_profile`,
 * `get_all_channels`, `get_ordered_list`, `create_link`, `log&real_action=play`) rather than
 * guessed from scratch - see the endpoints STBEMU itself hits in `dwx.class`.
 *
 * One instance = one session. There is deliberately no reconnect/retry state machine in here -
 * [IptvViewModel] owns that by discarding this client and constructing a fresh one, which keeps
 * "what does a from-scratch connection attempt look like" simple to reason about in one place.
 */
class StalkerPortalClient(
    private val portalUrl: String,
    private val macAddress: String,
) {
    private var resolvedEndpoint: String? = null
    private var token: String = ""
    private val timezoneId: String = java.util.TimeZone.getDefault().id

    /** Non-null once [connect] has completed and `get_profile` came back with a `block_msg` -
     * e.g. no credit/expired subscription/banned MAC. Deliberately doesn't fail [connect] itself
     * (see that function's doc comment): the handshake/token are still perfectly valid for
     * [fetchAccountInfo] even while blocked from actually streaming, which is exactly what a
     * blocked-account screen needs to show *why* and *until when*. */
    var blockMessage: String? = null
        private set

    /** Handshake + get_profile. Must succeed before [fetchChannels]/[resolveStreamUrl] - but
     * succeeding here does *not* mean the account can stream: a blocked/expired account still
     * completes a handshake and gets a token (real STB firmware needs that much just to show its
     * own "blocked" screen), it only sets [blockMessage] rather than failing outright. Callers
     * that actually need working playback (fetchChannels, resolveStreamUrl, recording) must check
     * [blockMessage] themselves after a successful connect - see [IptvViewModel.beginConnect] and
     * [IptvRecordingEngine]'s session acquisition. */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = resolveEndpoint()
            resolvedEndpoint = endpoint
            token = handshake(endpoint)
            getProfile(endpoint, token)
        }
    }

    suspend fun fetchChannels(): Result<List<StalkerChannel>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = resolvedEndpoint ?: error("connect() must succeed first")
            val js = request(endpoint, mapOf("type" to "itv", "action" to "get_all_channels"))
            val data = js.optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { i ->
                val obj = data.optJSONObject(i) ?: return@mapNotNull null
                val cmd = obj.optString("cmd").ifBlank { return@mapNotNull null }
                val number = obj.optString("number")
                StalkerChannel(
                    id = obj.optString("id"),
                    number = number,
                    name = obj.optString("name").ifBlank { "Channel $number" },
                    logoUrl = obj.optString("logo").ifBlank { null },
                    cmd = cmd,
                    genreId = obj.optString("tv_genre_id"),
                    censored = obj.optString("censored") == "1",
                )
            }
        }
    }

    /** Confirmed live against edge.bz: `{"js":[{"id":"*","title":"All",...}, {"id":"3",
     * "title":"ENGLISH",...}, ...]}` - same array-shaped `js` as [fetchShortEpg]. Best-effort:
     * a portal that doesn't support genres (or a transient failure) just means no category tabs,
     * not a broken connection - [IptvViewModel] falls back to a synthetic "All" entry rather than
     * failing the whole connect over it. Every genre also carries a `censored` flag (0/1) -
     * confirmed live that [fetchChannels] silently excludes every channel in a censored genre
     * from its response entirely, which is why those need [fetchCensoredGenreChannels] instead. */
    suspend fun fetchGenres(): Result<List<StalkerGenre>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = resolvedEndpoint ?: error("connect() must succeed first")
            val js = requestArray(endpoint, mapOf("type" to "itv", "action" to "get_genres"))
            (0 until js.length()).mapNotNull { i ->
                val obj = js.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").ifBlank { return@mapNotNull null }
                StalkerGenre(
                    id = id,
                    title = obj.optString("title").ifBlank { "Unknown" },
                    censored = obj.optString("censored") == "1",
                )
            }
        }
    }

    /** [fetchChannels] (`get_all_channels`) silently excludes every censored genre's channels
     * from its response entirely - confirmed live against edge.bz (3,004 channels back, zero
     * from the one censored genre on this portal). The only way to actually list them is
     * per-genre via `get_ordered_list`, which - unlike `get_all_channels` - is paginated (14
     * items/page, confirmed live, not configurable via any param), so this loops pages until the
     * portal's own `total_items` is satisfied or a page comes back empty.
     *
     * Deliberately not folded into [fetchChannels]/`connect()` - real portals cap out around a
     * couple hundred censored channels here, meaning ~25 extra requests, which is only worth
     * paying for a genre parental control has actually unlocked, not for every connection. */
    suspend fun fetchCensoredGenreChannels(genreId: String): Result<List<StalkerChannel>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = resolvedEndpoint ?: error("connect() must succeed first")
            val results = mutableListOf<StalkerChannel>()
            var page = 1
            while (page <= MAX_CENSORED_GENRE_PAGES) {
                val js = request(
                    endpoint,
                    mapOf("type" to "itv", "action" to "get_ordered_list", "genre" to genreId, "p" to page.toString()),
                )
                val data = js.optJSONArray("data") ?: JSONArray()
                if (data.length() == 0) break
                (0 until data.length()).mapNotNullTo(results) { i ->
                    val obj = data.optJSONObject(i) ?: return@mapNotNullTo null
                    val cmd = obj.optString("cmd").ifBlank { return@mapNotNullTo null }
                    val number = obj.optString("number")
                    StalkerChannel(
                        id = obj.optString("id"),
                        number = number,
                        name = obj.optString("name").ifBlank { "Channel $number" },
                        logoUrl = obj.optString("logo").ifBlank { null },
                        cmd = cmd,
                        genreId = obj.optString("tv_genre_id").ifBlank { genreId },
                        censored = true,
                    )
                }
                if (results.size >= js.optInt("total_items", 0)) break
                page++
            }
            results
        }
    }

    /** `cmd` from [fetchChannels] is a portal-internal play directive, not a playable URL - this
     * exchanges it for the actual stream URL the player can open, per-play (some portals rotate
     * tokens embedded in the returned URL, so it's not something to resolve once and cache). */
    suspend fun resolveStreamUrl(channel: StalkerChannel): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = resolvedEndpoint ?: error("connect() must succeed first")
            val js = request(
                endpoint,
                mapOf(
                    "type" to "itv",
                    "action" to "create_link",
                    "cmd" to channel.cmd,
                    "forced_storage" to "undefined",
                    "disable_ad" to "0",
                ),
            )
            val raw = js.optString("cmd").ifBlank {
                throw StalkerPortalError.InvalidResponse("Portal returned no stream URL for ${channel.name}")
            }
            extractStreamUrl(raw)
        }
    }

    /** `type=vod&action=get_categories` - the standard Ministra/Stalker-portal-fork shape for
     * movie categories, analogous to [fetchGenres] for live TV. Unlike [fetchGenres] this has
     * *not* been confirmed live against this app's actual portal - if a provider's VOD catalog
     * uses different field names this comes back empty (best-effort, same as [fetchGenres]) and
     * [IptvViewModel] shows the Movies tab as unsupported rather than failing anything else. */
    suspend fun fetchVodCategories(): Result<List<StalkerVodCategory>> = withContext(Dispatchers.IO) {
        runCatching { fetchCategories(type = "vod") }
    }

    /** `type=series&action=get_categories` - see [fetchVodCategories]'s doc comment; same
     * unverified-shape caveat applies. */
    suspend fun fetchSeriesCategories(): Result<List<StalkerVodCategory>> = withContext(Dispatchers.IO) {
        runCatching { fetchCategories(type = "series") }
    }

    private fun fetchCategories(type: String): List<StalkerVodCategory> {
        val endpoint = resolvedEndpoint ?: error("connect() must succeed first")
        val js = requestArray(endpoint, mapOf("type" to type, "action" to "get_categories"))
        return (0 until js.length()).mapNotNull { i ->
            val obj = js.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id").ifBlank { return@mapNotNull null }
            StalkerVodCategory(id = id, title = obj.optString("title").ifBlank { "Unknown" })
        }
    }

    /** `type=vod&action=get_ordered_list&category=<id>` - movies in one category, fetched in a
     * bounded chunk rather than the whole category (see [VodPage]'s doc comment: some providers'
     * catalogs run into the thousands, and nothing here needs more than what's currently visible/
     * searched). [IptvViewModel.selectVodCategory] calls this with [startPage] 1 for the first
     * chunk; [IptvViewModel.loadMoreVodItems] resumes from [VodPage.nextPage] for each further
     * one. Same unverified-shape caveat as [fetchVodCategories]. */
    suspend fun fetchVodItems(categoryId: String, startPage: Int = 1, limit: Int = 100): Result<VodPage> = withContext(Dispatchers.IO) {
        runCatching { fetchOrderedList(type = "vod", categoryId = categoryId, contentType = VodContentType.MOVIES, startPage = startPage, limit = limit) }
    }

    /** `type=series&action=get_ordered_list&category=<id>` - series entries in one category,
     * shipped as a flat list (see [StalkerVodItem]'s doc comment): each entry plays/downloads
     * through the same `cmd` a movie would, with no season/episode drill-down in this version.
     * Chunked the same way as [fetchVodItems]. */
    suspend fun fetchSeriesItems(categoryId: String, startPage: Int = 1, limit: Int = 100): Result<VodPage> = withContext(Dispatchers.IO) {
        runCatching { fetchOrderedList(type = "series", categoryId = categoryId, contentType = VodContentType.SERIES, startPage = startPage, limit = limit) }
    }

    /** "Deep search" (see [IptvSearchMode.DEEP]) for movies - `category=<`[ALL_GENRES_ID]`>` plus
     * a `search` param, asking the portal itself to match against its whole VOD catalog rather
     * than whatever chunk of one category happens to be loaded locally. `search` as a
     * `get_ordered_list` parameter is the standard Ministra/Stalker-fork shape for this, same
     * unverified-against-this-provider caveat as the rest of this file's VOD methods - and unlike
     * [matchesSearch]'s accent-insensitive local matching, whether *this* is accent/case-
     * insensitive depends entirely on the portal's own search implementation, outside this app's
     * control. Chunked exactly like [fetchVodItems] in case a query matches a lot. */
    suspend fun searchVod(query: String, startPage: Int = 1, limit: Int = 100): Result<VodPage> = withContext(Dispatchers.IO) {
        runCatching {
            fetchOrderedList(type = "vod", categoryId = ALL_GENRES_ID, contentType = VodContentType.MOVIES, startPage = startPage, limit = limit, search = query)
        }
    }

    /** Same as [searchVod], for series. */
    suspend fun searchSeries(query: String, startPage: Int = 1, limit: Int = 100): Result<VodPage> = withContext(Dispatchers.IO) {
        runCatching {
            fetchOrderedList(type = "series", categoryId = ALL_GENRES_ID, contentType = VodContentType.SERIES, startPage = startPage, limit = limit, search = query)
        }
    }

    /** Pages from [startPage] until either [limit] items are collected or a page comes back
     * empty - deliberately not also comparing against the portal's own `total_items` the way
     * [fetchCensoredGenreChannels] does: that field describes the *whole category*, not what's
     * left after some number of earlier chunks, and threading a running total through every
     * caller just to use it as a second, redundant end-of-catalog signal isn't worth it when an
     * empty page already means the same thing unambiguously. The one cost is a single extra
     * request, at the very end of a category, that comes back empty - harmless. [search], when
     * non-blank, is [searchVod]/[searchSeries]'s deep search; omitted, this is ordinary
     * category browsing ([fetchVodItems]/[fetchSeriesItems]). */
    private fun fetchOrderedList(
        type: String,
        categoryId: String,
        contentType: VodContentType,
        startPage: Int,
        limit: Int,
        search: String? = null,
    ): VodPage {
        val endpoint = resolvedEndpoint ?: error("connect() must succeed first")
        val results = mutableListOf<StalkerVodItem>()
        var page = startPage
        var reachedEnd = false
        var pagesFetched = 0
        var totalItems = 0
        val baseParams = mapOf("type" to type, "action" to "get_ordered_list", "category" to categoryId) +
            (if (search.isNullOrBlank()) emptyMap() else mapOf("search" to search))
        while (results.size < limit && pagesFetched < MAX_PAGES_PER_BATCH) {
            val js = request(endpoint, baseParams + mapOf("p" to page.toString()))
            val data = js.optJSONArray("data") ?: JSONArray()
            pagesFetched++
            totalItems = js.optInt("total_items", totalItems)
            if (data.length() == 0) {
                reachedEnd = true
                break
            }
            (0 until data.length()).mapNotNullTo(results) { i ->
                val obj = data.optJSONObject(i) ?: return@mapNotNullTo null
                val cmd = obj.optString("cmd").ifBlank { return@mapNotNullTo null }
                val id = obj.optString("id").ifBlank { return@mapNotNullTo null }
                StalkerVodItem(
                    id = id,
                    name = obj.optString("name").ifBlank { "Untitled" },
                    cmd = cmd,
                    categoryId = categoryId,
                    logoUrl = obj.optString("screenshot_uri").ifBlank { obj.optString("logo").ifBlank { null } },
                    contentType = contentType,
                    description = obj.optString("description").ifBlank { null },
                    ratingImdb = obj.optString("rating_imdb").ifBlank { null },
                    ratingTomatoes = firstNonBlank(obj, "rating_tomatoes", "rating_rt", "rating_rotten_tomatoes"),
                )
            }
            page++
        }
        return VodPage(items = results, nextPage = page, hasMore = !reachedEnd, totalItems = totalItems)
    }

    /** `type=vod&action=create_link&cmd=<item.cmd>` - the same action Ministra-style portals use
     * to resolve both movies and series entries to a playable URL, mirroring how [resolveStreamUrl]
     * does it for live channels. */
    suspend fun resolveVodStreamUrl(item: StalkerVodItem): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = resolvedEndpoint ?: error("connect() must succeed first")
            val js = request(
                endpoint,
                mapOf("type" to "vod", "action" to "create_link", "cmd" to item.cmd, "forced_storage" to "undefined", "disable_ad" to "0"),
            )
            val raw = js.optString("cmd").ifBlank {
                throw StalkerPortalError.InvalidResponse("Portal returned no stream URL for ${item.name}")
            }
            extractStreamUrl(raw)
        }
    }

    /** Next few programs for one channel, in order - confirmed live against edge.bz (empty
     * `[]` for channels the portal genuinely has no schedule for, real entries otherwise).
     * [count] trades off request cost against how far into the guide's time window this
     * channel's row will actually be filled - callers request this lazily, per row, as it
     * scrolls into view rather than for the whole channel list up front. */
    suspend fun fetchShortEpg(channel: StalkerChannel, count: Int = 10): Result<List<EpgProgram>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = resolvedEndpoint ?: error("connect() must succeed first")
            val js = requestArray(
                endpoint,
                mapOf("type" to "itv", "action" to "get_short_epg", "ch_id" to channel.id, "size" to count.toString()),
            )
            (0 until js.length()).mapNotNull { i ->
                val obj = js.optJSONObject(i) ?: return@mapNotNull null
                val start = obj.optLong("start_timestamp", -1)
                val stop = obj.optLong("stop_timestamp", -1)
                if (start < 0 || stop <= start) return@mapNotNull null
                EpgProgram(
                    id = obj.optString("id"),
                    title = obj.optString("name").ifBlank { "Unknown" },
                    description = obj.optString("descr"),
                    startEpochSeconds = start,
                    endEpochSeconds = stop,
                )
            }
        }
    }

    /** Portals commonly prefix the real URL with a player directive (e.g. `ffmpeg http://...`)
     * instead of returning it bare - the last whitespace-separated token that looks like a URL is
     * the actual stream. */
    private fun extractStreamUrl(raw: String): String =
        raw.trim().split(Regex("\\s+")).lastOrNull { it.startsWith("http") } ?: raw.trim()

    /** Portal software (Stalker, Ministra, and their various forks) disagrees on where `portal.php`
     * actually lives, so a bare host/URL is tried against the handful of paths real STB firmwares
     * fall back through, in order, stopping at the first one that completes a handshake. If the
     * user already pasted a full `.php` URL, that's used as-is.
     *
     * Critically, a portal handed out as `.../c/` means "c" is the STB web client's own page
     * path, not a directory the API lives under - `portal.php`/`load.php` sit as a *sibling* of
     * `c/`, one level up, not inside it (confirmed live against edge.bz: `/c/portal.php` 404s,
     * `/portal.php` at the same root handshakes fine). So both the literal path given and its
     * parent (with a trailing `/c` stripped) are tried. */
    private fun resolveEndpoint(): String {
        val trimmed = portalUrl.trim().trimEnd('/')
        require(trimmed.isNotBlank()) { "Portal URL is empty" }
        val candidates = if (trimmed.endsWith(".php")) {
            listOf(trimmed)
        } else {
            val bases = listOfNotNull(trimmed, trimmed.removeSuffix("/c").takeIf { it != trimmed }).distinct()
            bases.flatMap { base ->
                listOf(
                    "$base/portal.php",
                    "$base/stalker_portal/server/load.php",
                    "$base/server/load.php",
                )
            }.distinct()
        }
        var lastError: Exception? = null
        for (candidate in candidates) {
            try {
                handshake(candidate)
                return candidate
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: StalkerPortalError.Network("Could not reach the portal")
    }

    private fun handshake(endpoint: String): String {
        val js = request(endpoint, mapOf("type" to "stb", "action" to "handshake"), authToken = "")
        return js.optString("token").ifBlank { throw StalkerPortalError.InvalidResponse("Portal handshake returned no token") }
    }

    private fun getProfile(endpoint: String, authToken: String) {
        val serial = macAddress.replace(":", "").takeLast(8).ifBlank { "00000000" }
        val params = mapOf(
            "type" to "stb",
            "action" to "get_profile",
            "hd" to "1",
            "ver" to "ImageDescription: 0.2.18-r23-250; ImageDate: Wed Aug 29 10:49:53 EEST 2018",
            "num_banks" to "2",
            "sn" to serial,
            "stb_type" to "MAG250",
            "client_type" to "STB",
            "image_version" to "218",
            "video_out" to "hdmi",
            "device_id" to "",
            "device_id2" to "",
            "signature" to "",
            "auth_second_step" to "1",
            "hw_version" to "1.7-BD-00",
            "not_valid_token" to "0",
            "timezone" to timezoneId,
        )
        val js = request(endpoint, params, authToken)
        blockMessage = js.optString("block_msg").ifBlank { null }
    }

    /** `type=account_info&action=get_main_info` - the standard Ministra/Stalker-portal-fork
     * action for subscriber/account details (login, expiry, contact info), separate from
     * `get_profile`'s device-registration fields. Not confirmed live against this app's actual
     * portal (same caveat as [fetchVodCategories]): field names below are the common shape, but a
     * provider that customizes this may return some/none of them, so every field except [mac] is
     * nullable and a missing one just means that field isn't shown in the account card rather
     * than the whole fetch failing. Safe to call whether or not [blockMessage] is set - a blocked
     * account still needs this to show why, and until when. */
    suspend fun fetchAccountInfo(): Result<StalkerAccountInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = resolvedEndpoint ?: error("connect() must succeed first")
            val js = request(endpoint, mapOf("type" to "account_info", "action" to "get_main_info"))
            StalkerAccountInfo(
                mac = macAddress,
                login = firstNonBlank(js, "login", "id"),
                fullName = firstNonBlank(js, "full_name", "fname", "name"),
                phone = firstNonBlank(js, "phone", "phone_number"),
                email = firstNonBlank(js, "email"),
                tariffPlan = firstNonBlank(js, "tariff_plan", "tariff"),
                expiryEpochSeconds = parsePortalDate(firstNonBlank(js, "end_date", "tariff_expired_date", "expire_billing_date")),
                blocked = blockMessage != null,
                blockMessage = blockMessage,
            )
        }
    }

    private fun firstNonBlank(js: JSONObject, vararg keys: String): String? =
        keys.asSequence().map { js.optString(it) }.firstOrNull { it.isNotBlank() && it != "0000-00-00 00:00:00" }

    /** Portals overwhelmingly send account dates as `yyyy-MM-dd HH:mm:ss` in their own local
     * time - confirmed only by convention across public Stalker/Ministra client implementations,
     * not against this app's actual portal (same caveat as the rest of `account_info`). A date
     * that fails to parse just means the account card omits an expiry rather than crashing. */
    private fun parsePortalDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        raw.toLongOrNull()?.let { return it } // some forks send a raw epoch instead
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(raw)?.time?.let { it / 1000L }
        }.getOrNull()
    }

    /** Most actions (`handshake`, `get_profile`, `create_link`, `get_all_channels`) wrap their
     * payload as `{"js": {...}}` - an object. */
    private fun request(endpoint: String, params: Map<String, String>, authToken: String = token): JSONObject =
        fetchRoot(endpoint, params, authToken).optJSONObject("js")
            ?: throw StalkerPortalError.InvalidResponse("Unexpected portal response shape")

    /** A handful (`get_genres`, `get_short_epg`) instead wrap it as `{"js": [...]}` - an array
     * directly, not an object with a `data` field. */
    private fun requestArray(endpoint: String, params: Map<String, String>, authToken: String = token): JSONArray =
        fetchRoot(endpoint, params, authToken).optJSONArray("js")
            ?: throw StalkerPortalError.InvalidResponse("Unexpected portal response shape")

    private fun fetchRoot(endpoint: String, params: Map<String, String>, authToken: String): JSONObject {
        val query = (params + mapOf("JsHttpRequest" to "1-xml")).entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val connection = URL("$endpoint?$query").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 4 rev: 250 Safari/533.3",
        )
        connection.setRequestProperty("X-User-Agent", "Model: MAG250; Link: WiFi")
        connection.setRequestProperty("Cookie", "mac=$macAddress; stb_lang=en; timezone=$timezoneId")
        if (authToken.isNotBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $authToken")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let { s -> BufferedReader(InputStreamReader(s)).use { it.readText() } }.orEmpty()
            if (code !in 200..299) throw StalkerPortalError.Network("Portal returned HTTP $code")
            if (body.isBlank()) throw StalkerPortalError.InvalidResponse("Empty response from portal")
            JSONObject(body)
        } catch (e: StalkerPortalError) {
            throw e
        } catch (e: Exception) {
            throw StalkerPortalError.Network(e.message ?: "Network error")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000

        /** Hard cap on [fetchCensoredGenreChannels]'s pagination loop - protects against a
         * pathological/misreported `total_items` looping forever; 200 pages x 14 items is far
         * beyond any real censored-genre channel count seen in practice (360 on edge.bz). */
        private const val MAX_CENSORED_GENRE_PAGES = 200

        /** Safety cap on a single [fetchVodItems]/[fetchSeriesItems] chunk's own page-fetch loop -
         * a VOD/series category's page size isn't confirmed against this provider, so this
         * protects against a pathologically small one (e.g. 1-2 items/page) looping far past the
         * chunk's own `limit` worth of requests; any real portal's page size reaches `limit`
         * (default 100) well before this. */
        private const val MAX_PAGES_PER_BATCH = 100
    }
}
