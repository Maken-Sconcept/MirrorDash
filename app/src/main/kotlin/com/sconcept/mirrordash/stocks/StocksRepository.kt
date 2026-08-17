package com.sconcept.mirrordash.stocks

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class StockQuote(
    val symbol: String,
    val price: Double?,
    val changePercent: Double?,
)

/** Free, keyless quotes from Stooq's CSV endpoint - same "no secrets to manage" approach
 * [com.sconcept.mirrordash.weather.WeatherRepository] uses for weather. Stooq doesn't expose a
 * previous-close field on this endpoint, so `changePercent` is the day's open-to-last move
 * rather than a true previous-close change - good enough for a glanceable ticker, not a trading
 * tool. */
class StocksRepository {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }

    fun fetchQuotes(symbols: List<String>): Result<List<StockQuote>> = runCatching {
        val cleaned = symbols.map { it.trim() }.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return@runCatching emptyList()

        val joined = cleaned.joinToString(",") { URLEncoder.encode(it, StandardCharsets.UTF_8.name()) }
        val url = "https://stooq.com/q/l/?s=$joined&f=sd2t2ohlc&e=csv"
        val lines = readCsv(url)
        if (lines.size <= 1) return@runCatching emptyList()

        lines.drop(1).mapNotNull { line ->
            val cols = line.split(",")
            if (cols.size < 7) return@mapNotNull null
            val symbol = cols[0].trim('"')
            val open = cols[3].toDoubleOrNull()
            val close = cols[6].toDoubleOrNull()
            val changePercent = if (open != null && close != null && open != 0.0) {
                ((close - open) / open) * 100.0
            } else {
                null
            }
            StockQuote(symbol = symbol.uppercase(), price = close, changePercent = changePercent)
        }
    }

    private fun readCsv(url: String): List<String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            require(code in 200..299) { "Stocks service error." }
            BufferedReader(InputStreamReader(stream)).useLines { it.toList() }
        } finally {
            connection.disconnect()
        }
    }
}
