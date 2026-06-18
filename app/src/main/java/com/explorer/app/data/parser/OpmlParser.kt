package com.explorer.app.data.parser

import android.util.Xml
import com.explorer.app.data.db.RssSource
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object OpmlParser {

    fun parseOpml(xmlContent: String): List<RssSource> {
        val sources = mutableListOf<RssSource>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                    val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                    if (!xmlUrl.isNullOrEmpty()) {
                        val title = parser.getAttributeValue(null, "title")
                            ?: parser.getAttributeValue(null, "text")
                            ?: "RSS Feed"
                        sources.add(RssSource(xmlUrl, title))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sources
    }

    fun generateOpml(sources: List<RssSource>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"1.0\">\n")
        sb.append("  <head>\n")
        sb.append("    <title>The Explorer App Feeds Export</title>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")
        sb.append("    <outline text=\"Feeds\">\n")
        for (source in sources) {
            val titleEscaped = escapeXml(source.title)
            val urlEscaped = escapeXml(source.url)
            sb.append("      <outline type=\"rss\" text=\"$titleEscaped\" title=\"$titleEscaped\" xmlUrl=\"$urlEscaped\" htmlUrl=\"$urlEscaped\"/>\n")
        }
        sb.append("    </outline>\n")
        sb.append("  </body>\n")
        sb.append("</opml>")
        return sb.toString()
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
