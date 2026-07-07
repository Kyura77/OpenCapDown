package com.opencapdown.core.sources

import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject

internal class HtmlParserBridge {
    fun parse(html: String): org.jsoup.nodes.Document {
        return Jsoup.parse(html)
    }

    fun query(html: String, css: String): String {
        val doc = Jsoup.parse(html)
        val elements = doc.select(css)
        val arr = JSONArray()
        for (el in elements) {
            val obj = JSONObject()
            obj.put("tag", el.tagName())
            obj.put("text", el.text())
            obj.put("html", el.html())
            val attrs = JSONObject()
            for (attr in el.attributes()) {
                attrs.put(attr.key, attr.value)
            }
            obj.put("attrs", attrs)
            arr.put(obj)
        }
        return arr.toString()
    }
}
