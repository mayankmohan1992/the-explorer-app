package com.explorer.app.data.parser

import android.util.Xml
import com.explorer.app.data.db.RssSource
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object OpmlParser {

    private data class OutlineTag(val isFolder: Boolean, val name: String?)

    fun parseOpml(xmlContent: String): List<RssSource> {
        val sources = mutableListOf<RssSource>()
        val stack = mutableListOf<OutlineTag>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name.equals("outline", ignoreCase = true)) {
                            val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                            val title = parser.getAttributeValue(null, "title")
                                ?: parser.getAttributeValue(null, "text")
                                ?: "RSS Feed"
                            val isFolder = xmlUrl.isNullOrEmpty()
                            val currentCategory = stack.lastOrNull { it.isFolder }?.name
                            
                            if (!isFolder) {
                                sources.add(RssSource(xmlUrl!!, title, currentCategory))
                            }
                            stack.add(OutlineTag(isFolder, title))
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("outline", ignoreCase = true)) {
                            if (stack.isNotEmpty()) {
                                stack.removeAt(stack.size - 1)
                            }
                        }
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
        
        // Group sources by category
        val grouped = sources.groupBy { it.category }
        
        // Render categorized sources first
        grouped.forEach { (category, categorySources) ->
            if (!category.isNullOrEmpty()) {
                val categoryEscaped = escapeXml(category)
                sb.append("    <outline text=\"$categoryEscaped\" title=\"$categoryEscaped\">\n")
                for (source in categorySources) {
                    val titleEscaped = escapeXml(source.title)
                    val urlEscaped = escapeXml(source.url)
                    sb.append("      <outline type=\"rss\" text=\"$titleEscaped\" title=\"$titleEscaped\" xmlUrl=\"$urlEscaped\" htmlUrl=\"$urlEscaped\"/>\n")
                }
                sb.append("    </outline>\n")
            }
        }
        
        // Render uncategorized sources
        val uncategorized = (grouped[null] ?: emptyList()) + (grouped[""] ?: emptyList())
        if (uncategorized.isNotEmpty()) {
            for (source in uncategorized) {
                val titleEscaped = escapeXml(source.title)
                val urlEscaped = escapeXml(source.url)
                sb.append("    <outline type=\"rss\" text=\"$titleEscaped\" title=\"$titleEscaped\" xmlUrl=\"$urlEscaped\" htmlUrl=\"$urlEscaped\"/>\n")
            }
        }
        
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
