package com.opencapdown.core.sources

import com.opencapdown.core.common.OpenCapDownResult
import com.opencapdown.core.common.SourceError
import com.opencapdown.core.domain.models.ChapterInfo
import com.opencapdown.core.domain.models.MangaDetail
import com.opencapdown.core.domain.models.PageResult
import com.opencapdown.core.domain.models.SearchResult
import com.opencapdown.core.sources.model.JsSourceManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SourceManagerImpl(
    private val loader: JsSourceLoader,
    private val engineFactory: () -> QuickJsSourceEngine
) : SourceManager {
    override suspend fun search(sourceId: String, query: String): OpenCapDownResult<List<SearchResult>> =
        withContext(Dispatchers.Default) {
            runCatching {
                val code = loader.load(sourceId)
                val engine = engineFactory()
                engine.use {
                    it.loadSource(code)
                    val raw = it.invoke<List<Map<String, Any?>>>("search", query)
                    raw
                        .filter { result ->
                            val title = result["title"] as? String
                            val url = result["url"] as? String
                            val hasTitle = title != null && title.isNotBlank()
                            val hasUrl = url != null && url.isNotBlank()
                            hasTitle && hasUrl
                        }
                        .take(20)
                        .map { result ->
                            SearchResult(
                                sourceId = sourceId,
                                title = ((result["title"] as? String) ?: "").trim(),
                                coverUrl = (result["coverUrl"] as? String) ?: "",
                                url = (result["url"] as? String) ?: ""
                            )
                        }
                }
            }.fold(
                onSuccess = { OpenCapDownResult.Success(it) },
                onFailure = { OpenCapDownResult.Failure(SourceError.Unknown(it)) }
            )
        }

    override suspend fun getMangaDetail(sourceId: String, url: String): OpenCapDownResult<MangaDetail> =
        withContext(Dispatchers.Default) {
            runCatching {
                val code = loader.load(sourceId)
                val engine = engineFactory()
                engine.use {
                    it.loadSource(code)
                    val result = it.invoke<Map<String, Any?>>("getMangaDetail", url)
                    val chapters = (result["chapters"] as? List<Map<String, Any?>>)?.mapNotNull { c ->
                        val id = c["id"] as? String
                        val title = c["title"] as? String
                        val chapterUrl = c["url"] as? String
                        val number = (c["number"] as? Number)?.toFloat()
                        if (id.isNullOrBlank() || title.isNullOrBlank() || chapterUrl.isNullOrBlank() || number == null) null
                        else ChapterInfo(id = id, title = title.trim(), url = chapterUrl, number = number)
                    } ?: emptyList()
                    MangaDetail(
                        sourceId = sourceId,
                        url = url,
                        title = (result["title"] as? String)?.trim() ?: "",
                        coverUrl = (result["coverUrl"] as? String) ?: "",
                        description = (result["description"] as? String) ?: "",
                        status = (result["status"] as? String) ?: "",
                        chapters = chapters
                    )
                }
            }.fold(
                onSuccess = { OpenCapDownResult.Success(it) },
                onFailure = { OpenCapDownResult.Failure(SourceError.Unknown(it)) }
            )
        }

    override suspend fun getChapterPages(sourceId: String, url: String): OpenCapDownResult<List<PageResult>> =
        withContext(Dispatchers.Default) {
            runCatching {
                val code = loader.load(sourceId)
                val engine = engineFactory()
                engine.use {
                    it.loadSource(code)
                    val raw = it.invoke<List<Map<String, Any?>>>("getChapterPages", url)
                    raw
                        .filter { p -> !(p["imageUrl"] as? String).isNullOrBlank() }
                        .mapIndexed { index, p ->
                            PageResult(
                                index = index,
                                imageUrl = p["imageUrl"] as String,
                                headers = @Suppress("UNCHECKED_CAST") (p["headers"] as? Map<String, String>) ?: emptyMap()
                            )
                        }
                }
            }.fold(
                onSuccess = { OpenCapDownResult.Success(it) },
                onFailure = { OpenCapDownResult.Failure(SourceError.Unknown(it)) }
            )
        }

    override fun listSources(): List<JsSourceManifest> =
        loader.listAvailable().map { id ->
            val code = loader.load(id)
            JsSourceManifest(
                id = id,
                name = extractStringField(code, "name") ?: id,
                lang = extractStringField(code, "lang") ?: "",
                baseUrl = extractStringField(code, "baseUrl") ?: ""
            )
        }

    private fun extractStringField(code: String, field: String): String? {
        val regex = Regex("""$field\s*:\s*["']([^"']+)["']""")
        return regex.find(code)?.groupValues?.get(1)
    }
}
