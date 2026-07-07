package com.opencapdown.core.sources

import org.jsoup.Jsoup

internal class HtmlParserBridge {
    fun parse(html: String): org.jsoup.nodes.Document {
        return Jsoup.parse(html)
    }
}
