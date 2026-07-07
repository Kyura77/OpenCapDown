package com.opencapdown.core.sources

import com.opencapdown.core.common.OpenCapDownResult
import com.opencapdown.core.domain.models.MangaDetail
import com.opencapdown.core.domain.models.PageResult
import com.opencapdown.core.domain.models.SearchResult
import com.opencapdown.core.sources.model.JsSourceManifest

interface SourceManager {
    suspend fun search(sourceId: String, query: String): OpenCapDownResult<List<SearchResult>>
    suspend fun getMangaDetail(sourceId: String, url: String): OpenCapDownResult<MangaDetail>
    suspend fun getChapterPages(sourceId: String, url: String): OpenCapDownResult<List<PageResult>>
    fun listSources(): List<JsSourceManifest>
}
