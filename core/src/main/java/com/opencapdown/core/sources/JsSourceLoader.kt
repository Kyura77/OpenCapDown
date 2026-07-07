package com.opencapdown.core.sources

import android.content.Context

internal class JsSourceLoader(private val context: Context) {
    fun load(sourceId: String): String {
        return context.assets.open("sources/$sourceId.js").bufferedReader().use { it.readText() }
    }

    fun listAvailable(): List<String> {
        return context.assets.list("sources")
            ?.filter { it.endsWith(".js") && !it.startsWith("_") }
            ?.map { it.removeSuffix(".js") }
            ?: emptyList()
    }
}
