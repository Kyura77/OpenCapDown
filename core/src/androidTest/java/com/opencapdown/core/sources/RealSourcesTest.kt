package com.opencapdown.core.sources

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.opencapdown.core.common.Logger
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RealSourcesTest {

    private lateinit var client: OkHttpClient
    private lateinit var httpBridge: HttpBridge
    private lateinit var htmlParserBridge: HtmlParserBridge
    private lateinit var logger: Logger
    private lateinit var loader: JsSourceLoader
    private lateinit var engine: QuickJsSourceEngine

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        httpBridge = HttpBridge(client)
        htmlParserBridge = HtmlParserBridge()
        logger = object : Logger {
            override fun d(tag: String, msg: String) { Log.d("RealSourcesTest-$tag", msg) }
            override fun e(tag: String, msg: String, tr: Throwable?) { Log.e("RealSourcesTest-$tag", msg, tr) }
            override fun i(tag: String, msg: String) { Log.i("RealSourcesTest-$tag", msg) }
            override fun w(tag: String, msg: String) { Log.w("RealSourcesTest-$tag", msg) }
        }
        loader = JsSourceLoader(targetContext)
        engine = QuickJsSourceEngine(httpBridge, htmlParserBridge, logger)
    }

    @Test
    fun testListAvailableSources() {
        val sources = loader.listAvailable()
        Log.i("RealSourcesTest", "Fontes disponíveis encontradas: $sources")
        assertFalse("Nenhuma fonte JS encontrada no assets/sources/", sources.isEmpty())
    }

    @Test
    fun testMangaDexSearchAndPages() {
        val code = loader.load("mangadex")
        assertNotNull(code)
        engine.loadSource(code)

        // Test search
        Log.i("RealSourcesTest", "Iniciando busca no MangaDex...")
        val rawSearch = engine.invoke<List<Map<String, Any?>>>("search", "Solo Leveling")
        assertNotNull(rawSearch)
        Log.i("RealSourcesTest", "Busca MangaDex retornou ${rawSearch.size} itens")
        assertFalse(rawSearch.isEmpty())

        val firstManga = rawSearch.first()
        val mangaId = firstManga["url"] as String
        val title = firstManga["title"] as String
        Log.i("RealSourcesTest", "Primeiro mangá encontrado: $title (ID: $mangaId)")

        // Test details and chapters
        Log.i("RealSourcesTest", "Buscando detalhes do mangá $mangaId...")
        val rawDetail = engine.invoke<Map<String, Any?>>("getMangaDetail", mangaId)
        assertNotNull(rawDetail)
        
        val chapters = rawDetail["chapters"] as List<Map<String, Any?>>
        Log.i("RealSourcesTest", "Capítulos encontrados: ${chapters.size}")
        assertFalse(chapters.isEmpty())

        val firstChapter = chapters.first()
        val chapterId = firstChapter["url"] as String
        val chapterTitle = firstChapter["title"] as String
        Log.i("RealSourcesTest", "Primeiro capítulo: $chapterTitle (ID: $chapterId)")

        // Test pages
        Log.i("RealSourcesTest", "Buscando páginas do capítulo $chapterId...")
        val rawPages = engine.invoke<List<Map<String, Any?>>>("getChapterPages", chapterId)
        assertNotNull(rawPages)
        Log.i("RealSourcesTest", "Páginas retornadas: ${rawPages.size}")
        assertFalse("MangaDex retornou 0 páginas!", rawPages.isEmpty())
        
        val firstPage = rawPages.first()
        assertTrue(firstPage.containsKey("imageUrl"))
        Log.i("RealSourcesTest", "URL da primeira página: ${firstPage["imageUrl"]}")
    }

    @Test
    fun testLeitorDeMangasSearchAndPages() {
        val code = loader.load("leitordemangas")
        assertNotNull(code)
        engine.loadSource(code)

        Log.i("RealSourcesTest", "Iniciando busca no LeitorDeMangas...")
        val rawSearch = engine.invoke<List<Map<String, Any?>>>("search", "Naruto")
        assertNotNull(rawSearch)
        Log.i("RealSourcesTest", "Busca LeitorDeMangas retornou ${rawSearch.size} itens")
        if (rawSearch.isNotEmpty()) {
            val firstManga = rawSearch.first()
            val mangaUrl = firstManga["url"] as String
            Log.i("RealSourcesTest", "Buscando detalhes do mangá $mangaUrl...")
            val rawDetail = engine.invoke<Map<String, Any?>>("getMangaDetail", mangaUrl)
            val chapters = rawDetail["chapters"] as List<Map<String, Any?>>
            Log.i("RealSourcesTest", "Capítulos encontrados: ${chapters.size}")
            if (chapters.isNotEmpty()) {
                val chapterUrl = chapters.last()["url"] as String
                Log.i("RealSourcesTest", "Buscando páginas do capítulo $chapterUrl...")
                val rawPages = engine.invoke<List<Map<String, Any?>>>("getChapterPages", chapterUrl)
                Log.i("RealSourcesTest", "Páginas retornadas: ${rawPages.size}")
            }
        }
    }
}
