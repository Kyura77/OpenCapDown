package com.opencapdown.core.di

import android.content.Context
import com.opencapdown.core.OpenCapDownCore
import com.opencapdown.core.OpenCapDownCoreImpl
import com.opencapdown.core.common.AndroidLogger
import com.opencapdown.core.database.DatabaseModule
import com.opencapdown.core.database.daos.*
import com.opencapdown.core.downloads.*
import com.opencapdown.core.reader.*
import com.opencapdown.core.sources.*
import com.opencapdown.core.telegram.*
import okhttp3.OkHttpClient
import java.io.File

internal class CoreModule(private val context: Context) {
    private val database = DatabaseModule.provideDatabase(context)
    private val client = OkHttpClient.Builder().build()
    private val logger = AndroidLogger()

    fun createCore(version: String): OpenCapDownCore {
        val jsSourceLoader = JsSourceLoader(context)
        val sourceManager = SourceManagerImpl(
            loader = jsSourceLoader,
            engineFactory = { createSourceEngine() }
        )

        val imageDownloader = ImageDownloader(client)
        val downloadRepository = DownloadRepository(
            jobDao = database.downloadJobDao(),
            chapterDao = database.chapterDao(),
            pageDao = database.pageDao()
        )
        val downloadManager = DownloadManagerImpl(
            imageDownloader = imageDownloader,
            repository = downloadRepository,
            cacheDir = File(context.cacheDir, "downloads")
        )

        val telegramRateLimiter = TelegramRateLimiter()
        val telegramApiClient = TelegramApiClient(client, telegramRateLimiter)
        val telegramConfigProvider = EncryptedTelegramConfigProvider(context)
        val telegramTopicManager = TelegramTopicManager(
            mangaDao = database.libraryMangaDao(),
            apiClient = telegramApiClient,
            configProvider = telegramConfigProvider
        )
        val telegramSync = TelegramSyncImpl(
            apiClient = telegramApiClient,
            topicManager = telegramTopicManager,
            configProvider = telegramConfigProvider,
            chapterDao = database.chapterDao(),
            pageDao = database.pageDao(),
            mangaDao = database.libraryMangaDao(),
            cacheDir = File(context.cacheDir, "telegram")
        )

        val readingProgressTracker = ReadingProgressTracker(database.settingDao())
        val pageResolver = PageResolver(sourceManager, telegramSync, File(context.cacheDir, "pages"))
        val readerEngine = ReaderEngineImpl(
            chapterDao = database.chapterDao(),
            pageDao = database.pageDao(),
            pageResolver = pageResolver,
            progressTracker = readingProgressTracker
        )

        return OpenCapDownCoreImpl(
            version = version,
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            telegramSync = telegramSync,
            readerEngine = readerEngine,
            settingDao = database.settingDao(),
            libraryMangaDao = database.libraryMangaDao(),
            telegramConfigProvider = telegramConfigProvider,
            chapterDao = database.chapterDao()
        )
    }

    private fun createSourceEngine(): QuickJsSourceEngine {
        return QuickJsSourceEngine(
            httpBridge = HttpBridge(client),
            htmlParserBridge = HtmlParserBridge(),
            logger = logger
        )
    }
}
