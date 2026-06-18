package com.explorer.app.data.parser

import android.util.Xml
import com.explorer.app.data.db.RssArticle
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object RssParser {

    fun parse(inputStream: InputStream, sourceUrl: String): List<RssArticle> {
        val articles = mutableListOf<RssArticle>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentTitle = ""
        var currentLink = ""
        var currentDescription = ""
        var currentPubDate = ""
        var currentImageUrl: String? = null
        var insideItem = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                        insideItem = true
                    } else if (insideItem) {
                        when (name.lowercase()) {
                            "title" -> currentTitle = parser.nextText().trim()
                            "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                currentLink = href?.trim() ?: parser.nextText().trim()
                            }
                            "description", "summary", "content" -> {
                                val text = parser.nextText().trim()
                                currentDescription = cleanHtml(text)
                                if (currentImageUrl == null) {
                                    currentImageUrl = extractImageUrl(text)
                                }
                            }
                            "pubdate", "published", "updated" -> currentPubDate = parser.nextText().trim()
                            "enclosure" -> {
                                val type = parser.getAttributeValue(null, "type")
                                if (type != null && type.startsWith("image/")) {
                                    currentImageUrl = parser.getAttributeValue(null, "url")
                                }
                            }
                            "media:content" -> {
                                val medium = parser.getAttributeValue(null, "medium")
                                if (medium == "image") {
                                    currentImageUrl = parser.getAttributeValue(null, "url")
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                        articles.add(
                            RssArticle(
                                sourceUrl = sourceUrl,
                                title = currentTitle,
                                link = currentLink,
                                description = currentDescription,
                                pubDate = currentPubDate,
                                imageUrl = currentImageUrl
                            )
                        )
                        currentTitle = ""
                        currentLink = ""
                        currentDescription = ""
                        currentPubDate = ""
                        currentImageUrl = null
                        insideItem = false
                    }
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    private fun cleanHtml(html: String): String {
        // Remove HTML tags
        val noHtml = html.replace(Regex("<[^>]*>"), "")
        // Unescape standard characters
        return noHtml
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .trim()
    }

    private fun extractImageUrl(html: String): String? {
        val pattern = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1) else null
    }

    suspend fun fetchFeed(feedUrl: String): List<RssArticle> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = URL(feedUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.readTimeout = 8000
            connection.connectTimeout = 8000
            connection.requestMethod = "GET"
            connection.connect()
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { stream ->
                    parse(stream, feedUrl)
                }
            } else {
                emptyList()
            }
        }
    }
}
