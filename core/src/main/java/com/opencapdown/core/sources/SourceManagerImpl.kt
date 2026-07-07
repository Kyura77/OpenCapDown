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
                    it.invoke<List<Map<String, String>>>("search", query)
                        .map { result ->
                            SearchResult(
                                sourceId = sourceId,
                                title = result["title"]!!,
                                coverUrl = result["coverUrl"]!!,
                                url = result["url"]!!
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
                    MangaDetail(
                        sourceId = sourceId,
                        title = result["title"] as String,
                        coverUrl = result["coverUrl"] as String,
                        description = result["description"] as? String ?: "",
                        status = result["status"] as? String ?: "",
                        chapters = (result["chapters"] as? List<Map<String, Any?>>)?.map { c ->
                            ChapterInfo(
                                id = c["id"] as String,
                                title = c["title"] as String,
                                url = c["url"] as String,
                                number = (c["number"] as Number).toFloat()
                            )
                        } ?: emptyList()
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
                    it.invoke<List<Map<String, Any?>>>("getChapterPages", url)
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
        loader.listAvailable().map { JsSourceManifest(it, it, "") }
}
