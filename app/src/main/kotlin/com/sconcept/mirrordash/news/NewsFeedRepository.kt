package com.sconcept.mirrordash.news

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class NewsHeadline(val title: String, val link: String)

/** Parses an arbitrary user-supplied RSS 2.0 or Atom feed URL into headlines, using the
 * platform's built-in [Xml] pull parser rather than adding a feed-parsing dependency - same
 * "no new library for something the platform already does" approach the rest of the app follows
 * (see [com.sconcept.mirrordash.weather.WeatherRepository]'s plain org.json usage). */
class NewsFeedRepository {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }

    fun fetchHeadlines(feedUrl: String, limit: Int): Result<List<NewsHeadline>> = runCatching {
        val trimmed = feedUrl.trim()
        require(trimmed.isNotBlank()) { "No feed URL configured." }

        val connection = URL(trimmed).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        try {
            val code = connection.responseCode
            require(code in 200..299) { "News feed error." }
            parseFeed(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8), limit)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseFeed(reader: InputStreamReader, limit: Int): List<NewsHeadline> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(reader)

        val headlines = mutableListOf<NewsHeadline>()
        var inItem = false
        var currentTitle: String? = null
        var currentLink: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT && headlines.size < limit) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                    "item", "entry" -> {
                        inItem = true
                        currentTitle = null
                        currentLink = null
                    }
                    "title" -> if (inItem) currentTitle = readText(parser)
                    "link" -> if (inItem) {
                        val href = parser.getAttributeValue(null, "href")
                        currentLink = href ?: readText(parser)
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name.lowercase() in listOf("item", "entry")) {
                    inItem = false
                    val title = currentTitle?.trim()
                    if (!title.isNullOrBlank()) {
                        headlines.add(NewsHeadline(title = title, link = currentLink?.trim().orEmpty()))
                    }
                }
            }
            eventType = parser.next()
        }
        return headlines
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }
}
