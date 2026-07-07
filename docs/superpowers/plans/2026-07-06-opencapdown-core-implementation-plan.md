# OpenCapDown Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Android core engine that scrapes manga sources, manages offline downloads, syncs chapters with Telegram, and exposes a stable API for the UI layer.

**Architecture:** Modular Kotlin library split into feature modules (`sources`, `downloads`, `telegram`, `reader`, `database`, `common`). Sources are JavaScript files executed inside QuickJS with injected native helpers. All public contracts are interfaces behind a facade (`OpenCapDownCore`) versioned semantically. Local storage uses Room with migrations. Unit tests run on JVM with Robolectric/MockK; instrumented tests validate QuickJS and Telegram HTTP contracts.

**Tech Stack:** Kotlin 2.x, Android Gradle Plugin 8.x, QuickJS/J2V8, Room 2.6+, OkHttp 4.12+, WorkManager 2.9+, Kotlin Coroutines/Flow, JUnit 5, MockK, Robolectric, Turbine.

## Global Constraints

- Minimum SDK: Android 26 (Android 8.0).
- Target SDK: Android 34.
- All public APIs must be interfaces; implementations are internal.
- `OpenCapDownCore` facade is the **only** public entry point for the UI layer.
- Database schema changes require Room migration tests.
- Sources are JS files under `assets/sources/` and must not be compiled into Kotlin.
- No credentials hardcoded; use `EncryptedSharedPreferences` + Android KeyStore.
- All heavy work off main thread via coroutines/WorkManager.
- Every public function must have at least one unit test.
- README files must explain module purpose and file responsibilities for future AI agents.

---

## File Structure

```
OpenCapDown/
├── README.md                              # Visão geral do projeto para IA
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── app/                                   # UI layer (outra IA)
│   └── README.md
├── core/                                  # Módulo core engine
│   ├── README.md
│   ├── build.gradle.kts
│   └── src/main/java/com/opencapdown/core/
│       ├── OpenCapDownCore.kt             # Facade pública
│       ├── OpenCapDownCoreFactory.kt      # Cria instância versionada
│       ├── di/
│       │   └── CoreModule.kt              # Injeção manual (sem framework externo)
│       ├── common/
│       │   ├── Result.kt                  # Resultado tipado
│       │   ├── Logger.kt                  # Abstração de log
│       │   └── AndroidLogger.kt
│       ├── database/
│       │   ├── AppDatabase.kt
│       │   ├── DatabaseModule.kt
│       │   ├── entities/
│       │   │   ├── LibraryMangaEntity.kt
│       │   │   ├── ChapterEntity.kt
│       │   │   ├── PageEntity.kt
│       │   │   ├── DownloadJobEntity.kt
│       │   │   └── SettingEntity.kt
│       │   └── daos/
│       │       ├── LibraryMangaDao.kt
│       │       ├── ChapterDao.kt
│       │       ├── PageDao.kt
│       │       ├── DownloadJobDao.kt
│       │       └── SettingDao.kt
│       ├── domain/
│       │   ├── models/
│       │   │   ├── LibraryManga.kt
│       │   │   ├── Chapter.kt
│       │   │   ├── Page.kt
│       │   │   ├── DownloadJob.kt
│       │   │   ├── SearchResult.kt
│       │   │   ├── MangaDetail.kt
│       │   │   ├── ChapterInfo.kt
│       │   │   ├── PageResult.kt
│       │   │   ├── ChapterWithPages.kt
│       │   │   ├── ReadingProgress.kt
│       │   │   └── TelegramBackup.kt
│       │   └── SourceContract.kt
│       ├── sources/
│       │   ├── SourceManager.kt
│       │   ├── QuickJsSourceEngine.kt
│       │   ├── JsSourceLoader.kt
│       │   ├── HttpBridge.kt
│       │   ├── HtmlParserBridge.kt
│       │   ├── WebViewBridge.kt
│       │   └── model/
│       │       ├── JsSourceManifest.kt
│       │       └── SourceExecutionResult.kt
│       ├── downloads/
│       │   ├── DownloadManager.kt
│       │   ├── DownloadWorker.kt
│       │   ├── DownloadRepository.kt
│       │   ├── ImageDownloader.kt
│       │   └── DownloadConstraints.kt
│       ├── telegram/
│       │   ├── TelegramSync.kt
│       │   ├── TelegramApiClient.kt
│       │   ├── TelegramRateLimiter.kt
│       │   ├── TelegramTopicManager.kt
│       │   └── TelegramMessageParser.kt
│       └── reader/
│           ├── ReaderEngine.kt
│           ├── ReadingProgressTracker.kt
│           └── PageResolver.kt
├── core-test-fixtures/                    # Fakes/mocks compartilhados
│   └── README.md
└── sources/                               # Sources JS de exemplo
    └── src/main/assets/sources/
        ├── verdinha.js
        ├── mangadex.js
        └── _template.js
```

---

## Task 1: Project Scaffolding and Module Setup

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `README.md`
- Create: `app/README.md`
- Create: `core/README.md`
- Create: `core/build.gradle.kts`
- Create: `core-test-fixtures/README.md`
- Create: `core-test-fixtures/build.gradle.kts`
- Create: `sources/src/main/assets/sources/_template.js`

**Interfaces:**
- Produces: module structure, dependency catalog, README instructions for AI.

- [ ] **Step 1: Create root `README.md`**

```markdown
# OpenCapDown

Aplicativo Android para leitura de mangá via web scraping com backup no Telegram.

## Estrutura
- `app/` — UI layer. Outra IA cuida. Consome `core/`.
- `core/` — Engine backend: scraping, downloads, Telegram, leitor, banco.
- `core-test-fixtures/` — Fakes e stubs compartilhados entre testes.
- `sources/` — Sources JavaScript carregadas em runtime pelo `core/`.

## Como adicionar uma source
1. Crie `sources/src/main/assets/sources/{id}.js` seguindo `_template.js`.
2. Implemente `search`, `getMangaDetail`, `getChapterPages`.
3. Rode `./gradlew :core:testDebugUnitTest`.

## Contrato público
A UI NÃO acessa módulos internos. Use apenas `com.opencapdown.core.OpenCapDownCore`.
A interface é versionada semanticamente (`OpenCapDownCore.v1()`).
```

- [ ] **Step 2: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "OpenCapDown"
include(":app", ":core", ":core-test-fixtures", ":sources")
```

- [ ] **Step 3: Write `gradle/libs.versions.toml`**

```toml
[versions]
kotlin = "2.0.0"
agp = "8.5.0"
room = "2.6.1"
okhttp = "4.12.0"
work = "2.9.0"
coroutines = "1.8.0"
quickjs = "0.9.16" # ajustar para J2V8 ou quickjs-android real
junit5 = "5.10.2"
mockk = "1.13.10"
robolectric = "4.12.1"
turbine = "1.1.0"

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version = "1.13.0" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-security-crypto = { module = "androidx.security:security-crypto", version = "1.1.0-alpha06" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
quickjs = { module = "app.cash.quickjs:quickjs-android", version.ref = "quickjs" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit5" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "2.0.0-1.0.22" }
```

- [ ] **Step 4: Create `core/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.opencapdown.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.useJUnitPlatform() }
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.quickjs)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(project(":core-test-fixtures"))
}
```

- [ ] **Step 5: Create `core-test-fixtures/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.opencapdown.core.testfixtures"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mockk)
}
```

- [ ] **Step 6: Create `core/README.md` and `app/README.md`**

`core/README.md`:
```markdown
# Módulo `core`

Engine backend do OpenCapDown.

## Regras
- Toda classe pública deve ser uma interface.
- Implementações ficam em pacotes `internal`.
- A única porta de entrada é `OpenCapDownCore`.
- Testes unitários usam JVM + Robolectric. Testes de integração usam `MockWebServer`.

## Pacotes
- `common` — utilitários e abstrações.
- `database` — Room entities, DAOs, migrations.
- `domain` — modelos puros (data classes).
- `sources` — runtime JS para sources.
- `downloads` — fila e cache de downloads.
- `telegram` — backup/restore via Bot API.
- `reader` — resolução de páginas e progresso.
```

- [ ] **Step 7: Commit**

```bash
git add README.md settings.gradle.kts build.gradle.kts gradle/libs.versions.toml app/README.md core/ core-test-fixtures/ sources/
git commit -m "chore: scaffold OpenCapDown modules and dependencies"
```

---

## Task 2: Domain Models and Database Schema

**Files:**
- Create: `core/src/main/java/com/opencapdown/core/domain/models/*.kt`
- Create: `core/src/main/java/com/opencapdown/core/database/entities/*.kt`
- Create: `core/src/main/java/com/opencapdown/core/database/daos/*.kt`
- Create: `core/src/main/java/com/opencapdown/core/database/AppDatabase.kt`
- Create: `core/src/main/java/com/opencapdown/core/database/DatabaseModule.kt`
- Test: `core/src/test/java/com/opencapdown/core/database/DatabaseMigrationTest.kt`
- Test: `core/src/test/java/com/opencapdown/core/domain/ModelValidationTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `LibraryManga`, `Chapter`, `Page`, `DownloadJob`, `SearchResult`, `MangaDetail`, `ChapterInfo`, `PageResult`, `ChapterWithPages`, `ReadingProgress`, `TelegramBackup`. Room entities and DAOs.

- [ ] **Step 1: Write domain models**

```kotlin
// core/src/main/java/com/opencapdown/core/domain/models/LibraryManga.kt
data class LibraryManga(
    val id: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String,
    val status: String,
    val telegramTopicId: Int? = null
)
```

```kotlin
// core/src/main/java/com/opencapdown/core/domain/models/Chapter.kt
data class Chapter(
    val id: String,
    val mangaId: String,
    val number: Float,
    val title: String,
    val pageCount: Int,
    val integrityStatus: IntegrityStatus,
    val telegramAlbumMessageId: Long? = null,
    val isRead: Boolean = false
)

enum class IntegrityStatus {
    PENDING, COMPLETE, PARTIAL, BACKED_UP
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/domain/models/Page.kt
data class Page(
    val id: String,
    val chapterId: String,
    val index: Int,
    val localPath: String? = null,
    val telegramFileId: String? = null,
    val telegramMessageId: Long? = null
)
```

```kotlin
// core/src/main/java/com/opencapdown/core/domain/models/DownloadJob.kt
data class DownloadJob(
    val id: String,
    val chapterId: String,
    val status: DownloadStatus,
    val progress: Int = 0,
    val errorMessage: String? = null
)

enum class DownloadStatus {
    QUEUED, DOWNLOADING, COMPLETE, FAILED, CANCELLED
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/domain/models/SearchResult.kt
data class SearchResult(
    val sourceId: String,
    val title: String,
    val coverUrl: String,
    val url: String
)
```

```kotlin
// core/src/main/java/com/opencapdown/core/domain/models/MangaDetail.kt
data class MangaDetail(
    val sourceId: String,
    val title: String,
    val coverUrl: String,
    val description: String,
    val status: String,
    val chapters: List<ChapterInfo>
)

data class ChapterInfo(
    val id: String,
    val title: String,
    val url: String,
    val number: Float
)
```

```kotlin
// core/src/main/java/com/opencapdown/core/domain/models/PageResult.kt
data class PageResult(
    val index: Int,
    val imageUrl: String,
    val headers: Map<String, String> = emptyMap()
)
```

```kotlin
// core/src/main/java/com/opencapdown/core/domain/models/ChapterWithPages.kt
data class ChapterWithPages(
    val chapter: Chapter,
    val pages: List<Page>
)
```

```kotlin
// core/src/main/java/com/opencapdown/core/domain/models/ReadingProgress.kt
data class ReadingProgress(
    val mangaId: String,
    val chapterId: String,
    val pageIndex: Int
)
```

```kotlin
// core/src/main/java/com/opencapdown/core/domain/models/TelegramBackup.kt
data class TelegramBackup(
    val messageId: Long,
    val chapterTitle: String,
    val pageCount: Int,
    val createdAt: Long
)
```

- [ ] **Step 2: Write Room entities**

```kotlin
// core/src/main/java/com/opencapdown/core/database/entities/LibraryMangaEntity.kt
@Entity(tableName = "library_manga")
internal data class LibraryMangaEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String,
    val status: String,
    val telegramTopicId: Int?
)
```

```kotlin
// core/src/main/java/com/opencapdown/core/database/entities/ChapterEntity.kt
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = LibraryMangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mangaId"])]
)
internal data class ChapterEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val number: Float,
    val title: String,
    val pageCount: Int,
    val integrityStatus: String,
    val telegramAlbumMessageId: Long?,
    val isRead: Boolean
)
```

```kotlin
// core/src/main/java/com/opencapdown/core/database/entities/PageEntity.kt
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chapterId"])]
)
internal data class PageEntity(
    @PrimaryKey val id: String,
    val chapterId: String,
    val index: Int,
    val localPath: String?,
    val telegramFileId: String?,
    val telegramMessageId: Long?
)
```

```kotlin
// core/src/main/java/com/opencapdown/core/database/entities/DownloadJobEntity.kt
@Entity(tableName = "download_jobs")
internal data class DownloadJobEntity(
    @PrimaryKey val id: String,
    val chapterId: String,
    val status: String,
    val progress: Int,
    val errorMessage: String?
)
```

```kotlin
// core/src/main/java/com/opencapdown/core/database/entities/SettingEntity.kt
@Entity(tableName = "settings")
internal data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
```

- [ ] **Step 3: Write DAOs**

```kotlin
// core/src/main/java/com/opencapdown/core/database/daos/LibraryMangaDao.kt
@Dao
internal interface LibraryMangaDao {
    @Query("SELECT * FROM library_manga ORDER BY title ASC")
    fun observeAll(): Flow<List<LibraryMangaEntity>>

    @Query("SELECT * FROM library_manga WHERE id = :id")
    suspend fun getById(id: String): LibraryMangaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manga: LibraryMangaEntity)

    @Query("DELETE FROM library_manga WHERE id = :id")
    suspend fun delete(id: String)
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/database/daos/ChapterDao.kt
@Dao
internal interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY number ASC")
    fun observeByManga(mangaId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getById(id: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chapter: ChapterEntity)

    @Query("UPDATE chapters SET isRead = :isRead WHERE id = :id")
    suspend fun updateRead(id: String, isRead: Boolean)

    @Query("UPDATE chapters SET integrityStatus = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/database/daos/PageDao.kt
@Dao
internal interface PageDao {
    @Query("SELECT * FROM pages WHERE chapterId = :chapterId ORDER BY index ASC")
    suspend fun getByChapter(chapterId: String): List<PageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<PageEntity>)

    @Query("UPDATE pages SET localPath = :path WHERE id = :id")
    suspend fun updateLocalPath(id: String, path: String)
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/database/daos/DownloadJobDao.kt
@Dao
internal interface DownloadJobDao {
    @Query("SELECT * FROM download_jobs ORDER BY createdAt...")
    fun observeAll(): Flow<List<DownloadJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: DownloadJobEntity)

    @Query("DELETE FROM download_jobs WHERE id = :id")
    suspend fun delete(id: String)
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/database/daos/SettingDao.kt
@Dao
internal interface SettingDao {
    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingEntity)
}
```

- [ ] **Step 4: Write `AppDatabase.kt` and `DatabaseModule.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/database/AppDatabase.kt
@Database(
    entities = [
        LibraryMangaEntity::class,
        ChapterEntity::class,
        PageEntity::class,
        DownloadJobEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = true
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryMangaDao(): LibraryMangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun pageDao(): PageDao
    abstract fun downloadJobDao(): DownloadJobDao
    abstract fun settingDao(): SettingDao
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/database/DatabaseModule.kt
internal object DatabaseModule {
    fun provideDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "opencapdown.db"
        ).build()
    }
}
```

- [ ] **Step 5: Write mapper unit tests**

```kotlin
// core/src/test/java/com/opencapdown/core/database/EntityMappingTest.kt
class EntityMappingTest {
    @Test
    fun `chapter entity maps to domain and back`() {
        val domain = Chapter(
            id = "verdinha-123-c1",
            mangaId = "verdinha-123",
            number = 1f,
            title = "Capítulo 1",
            pageCount = 10,
            integrityStatus = IntegrityStatus.COMPLETE
        )
        val entity = domain.toEntity()
        val restored = entity.toDomain()
        assertEquals(domain, restored)
    }
}
```

- [ ] **Step 6: Run tests**

```bash
./gradlew :core:testDebugUnitTest
```
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/opencapdown/core/domain core/src/main/java/com/opencapdown/core/database core/src/test/java/com/opencapdown/core/database
git commit -m "feat: add domain models and Room database schema"
```

---

## Task 3: Source Engine with QuickJS

**Files:**
- Create: `core/src/main/java/com/opencapdown/core/sources/SourceManager.kt`
- Create: `core/src/main/java/com/opencapdown/core/sources/QuickJsSourceEngine.kt`
- Create: `core/src/main/java/com/opencapdown/core/sources/JsSourceLoader.kt`
- Create: `core/src/main/java/com/opencapdown/core/sources/HttpBridge.kt`
- Create: `core/src/main/java/com/opencapdown/core/sources/HtmlParserBridge.kt`
- Create: `core/src/main/java/com/opencapdown/core/sources/WebViewBridge.kt`
- Create: `core/src/main/java/com/opencapdown/core/sources/model/JsSourceManifest.kt`
- Create: `core/src/main/java/com/opencapdown/core/sources/model/SourceExecutionResult.kt`
- Create: `core/src/main/java/com/opencapdown/core/common/Logger.kt`
- Create: `core/src/main/java/com/opencapdown/core/common/AndroidLogger.kt`
- Create: `core/src/main/java/com/opencapdown/core/common/Result.kt`
- Test: `core/src/test/java/com/opencapdown/core/sources/QuickJsSourceEngineTest.kt`
- Test: `core/src/test/java/com/opencapdown/core/sources/JsSourceLoaderTest.kt`
- Create: `sources/src/main/assets/sources/_template.js`
- Create: `sources/src/main/assets/sources/mangadex.js`

**Interfaces:**
- Consumes: `Logger`, `HttpBridge`, `HtmlParserBridge`, `WebViewBridge`.
- Produces: `SourceManager` with methods `search`, `getMangaDetail`, `getChapterPages`.

- [ ] **Step 1: Write `Logger.kt` and `AndroidLogger.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/common/Logger.kt
interface Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/common/AndroidLogger.kt
internal class AndroidLogger : Logger {
    override fun d(tag: String, message: String) = Log.d(tag, message)
    override fun e(tag: String, message: String, throwable: Throwable?) = Log.e(tag, message, throwable)
}
```

- [ ] **Step 2: Write `Result.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/common/Result.kt
sealed class OpenCapDownResult<out T> {
    data class Success<T>(val data: T) : OpenCapDownResult<T>()
    data class Failure(val error: SourceError) : OpenCapDownResult<Nothing>()
}

sealed class SourceError {
    data class Network(val message: String) : SourceError()
    data class Parse(val message: String) : SourceError()
    data class SourceNotFound(val sourceId: String) : SourceError()
    data class Unknown(val throwable: Throwable) : SourceError()
}
```

- [ ] **Step 3: Write bridges**

```kotlin
// core/src/main/java/com/opencapdown/core/sources/HttpBridge.kt
internal class HttpBridge(private val client: OkHttpClient) {
    fun fetch(url: String, headers: Map<String, String> = emptyMap()): String {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        return client.newCall(request).execute().use { it.body?.string() ?: "" }
    }
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/sources/HtmlParserBridge.kt
internal class HtmlParserBridge {
    fun parse(html: String): JsoupDocumentWrapper {
        return JsoupDocumentWrapper(Jsoup.parse(html))
    }
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/sources/WebViewBridge.kt
// stub: implement via WebView when heavy Cloudflare detected
internal class WebViewBridge {
    // Intentionally minimal: real WebView rendering will be implemented in a dedicated anti-bot task.
    // Returning empty string makes Cloudflare-heavy sources fail gracefully until then.
    suspend fun fetchRenderedHtml(url: String): String = ""
}
```

- [ ] **Step 4: Write `JsSourceLoader.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/sources/JsSourceLoader.kt
internal class JsSourceLoader(private val context: Context) {
    fun load(sourceId: String): String {
        return context.assets.open("sources/$sourceId.js").bufferedReader().use { it.readText() }
    }

    fun listAvailable(): List<String> {
        return context.assets.list("sources")?.filter { it.endsWith(".js") }?.map { it.removeSuffix(".js") } ?: emptyList()
    }
}
```

- [ ] **Step 5: Write `QuickJsSourceEngine.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/sources/QuickJsSourceEngine.kt
internal class QuickJsSourceEngine(
    private val httpBridge: HttpBridge,
    private val htmlParserBridge: HtmlParserBridge,
    private val logger: Logger
) {
    private val runtime = QuickJs.create()

    init {
        runtime.set("__http", HttpBridgeJsInterface(httpBridge))
        runtime.set("__html", HtmlParserBridgeJsInterface(htmlParserBridge))
        runtime.set("__log", LoggerJsInterface(logger))
        runtime.evaluate("""
            globalThis.SourceEnv = {
                fetch: (url, opts) => __http.fetch(url, opts || {}),
                parseHtml: (html) => __html.parse(html),
                log: (level, msg) => __log.log(level, msg)
            };
        """.trimIndent())
    }

    fun loadSource(sourceCode: String) {
        runtime.evaluate(sourceCode)
    }

    fun <T> invoke(method: String, vararg args: Any?): T {
        return runtime.get("module").get(method).call(*args) as T
    }

    fun close() = runtime.close()
}
```

- [ ] **Step 6: Write `SourceManager.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/sources/SourceManager.kt
interface SourceManager {
    suspend fun search(sourceId: String, query: String): OpenCapDownResult<List<SearchResult>>
    suspend fun getMangaDetail(sourceId: String, url: String): OpenCapDownResult<MangaDetail>
    suspend fun getChapterPages(sourceId: String, url: String): OpenCapDownResult<List<PageResult>>
    fun listSources(): List<JsSourceManifest>
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/sources/SourceManagerImpl.kt
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

    override fun listSources(): List<JsSourceManifest> = loader.listAvailable().map { JsSourceManifest(it, it, "") }
}
```

- [ ] **Step 7: Write `_template.js` and `mangadex.js`**

`_template.js`:
```javascript
export default {
  id: "template",
  name: "Template Source",
  lang: "pt-BR",
  baseUrl: "https://example.com",

  async search(query) {
    SourceEnv.log("info", `search: ${query}`);
    return [];
  },

  async getMangaDetail(url) {
    SourceEnv.log("info", `detail: ${url}`);
    return { title: "", coverUrl: "", description: "", chapters: [] };
  },

  async getChapterPages(url) {
    SourceEnv.log("info", `pages: ${url}`);
    return [];
  }
}
```

`mangadex.js`:
```javascript
export default {
  id: "mangadex",
  name: "MangaDex",
  lang: "multi",
  baseUrl: "https://api.mangadex.org",

  async search(query) {
    const res = SourceEnv.fetch(`${this.baseUrl}/manga?title=${encodeURIComponent(query)}&limit=10`);
    const json = JSON.parse(res);
    return json.data.map(m => ({
      title: m.attributes.title.en || Object.values(m.attributes.title)[0],
      coverUrl: "",
      url: `${this.baseUrl}/manga/${m.id}`
    }));
  },

  async getMangaDetail(url) {
    const id = url.split("/").pop();
    const res = SourceEnv.fetch(`${this.baseUrl}/manga/${id}?includes[]=cover_art`);
    const json = JSON.parse(res);
    return {
      title: json.data.attributes.title.en,
      coverUrl: "",
      description: json.data.attributes.description.en || "",
      chapters: []
    };
  },

  async getChapterPages(url) {
    return [];
  }
}
```

- [ ] **Step 8: Write tests**

```kotlin
// core/src/test/java/com/opencapdown/core/sources/QuickJsSourceEngineTest.kt
@RunWith(RobolectricTestRunner::class)
class QuickJsSourceEngineTest {
    @Test
    fun `executes search from JS source`() {
        val engine = QuickJsSourceEngine(...)
        engine.loadSource("""
            export default {
                async search(query) {
                    return [{ title: "Test", coverUrl: "", url: "https://x/1" }];
                }
            }
        """.trimIndent())
        val result = engine.invoke<List<Map<String, String>>>("search", "x")
        assertEquals("Test", result.first()["title"])
        engine.close()
    }
}
```

- [ ] **Step 9: Run tests**

```bash
./gradlew :core:testDebugUnitTest
```
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add core/src/main/java/com/opencapdown/core/sources core/src/main/java/com/opencapdown/core/common core/src/test/java/com/opencapdown/core/sources sources/src/main/assets/sources
git commit -m "feat: add QuickJS source engine and JS source loader"
```

---

## Task 4: Download Manager

**Files:**
- Create: `core/src/main/java/com/opencapdown/core/downloads/DownloadManager.kt`
- Create: `core/src/main/java/com/opencapdown/core/downloads/DownloadRepository.kt`
- Create: `core/src/main/java/com/opencapdown/core/downloads/ImageDownloader.kt`
- Create: `core/src/main/java/com/opencapdown/core/downloads/DownloadConstraints.kt`
- Create: `core/src/main/java/com/opencapdown/core/downloads/DownloadWorker.kt` (WorkManager stub)
- Test: `core/src/test/java/com/opencapdown/core/downloads/DownloadManagerTest.kt`

**Interfaces:**
- Consumes: `SourceManager`, Room DAOs, file cache directory.
- Produces: `DownloadManager.enqueueChapter`, `observeQueue`, `cancel`.

- [ ] **Step 1: Write `ImageDownloader.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/downloads/ImageDownloader.kt
internal class ImageDownloader(private val client: OkHttpClient) {
    suspend fun download(url: String, headers: Map<String, String>, destination: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).apply {
                    headers.forEach { (k, v) -> addHeader(k, v) }
                }.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    destination.parentFile?.mkdirs()
                    destination.writeBytes(response.body!!.bytes())
                }
            }
        }
}
```

- [ ] **Step 2: Write `DownloadRepository.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/downloads/DownloadRepository.kt
internal class DownloadRepository(
    private val jobDao: DownloadJobDao,
    private val chapterDao: ChapterDao,
    private val pageDao: PageDao
) {
    fun observeQueue(): Flow<List<DownloadJob>> = jobDao.observeAll().map { it.map { e -> e.toDomain() } }

    suspend fun createJob(chapterId: String) {
        jobDao.insert(DownloadJobEntity(UUID.randomUUID().toString(), chapterId, DownloadStatus.QUEUED.name, 0, null))
    }

    suspend fun updateJob(job: DownloadJob) = jobDao.insert(job.toEntity())
    suspend fun deleteJob(jobId: String) = jobDao.delete(jobId)
}
```

- [ ] **Step 3: Write `DownloadManager.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/downloads/DownloadManager.kt
interface DownloadManager {
    suspend fun enqueueChapter(mangaId: String, chapterId: String)
    fun observeQueue(): Flow<List<DownloadJob>>
    suspend fun cancel(jobId: String)
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/downloads/DownloadManagerImpl.kt
internal class DownloadManagerImpl(
    private val sourceManager: SourceManager,
    private val imageDownloader: ImageDownloader,
    private val repository: DownloadRepository,
    private val cacheDir: File,
    private val constraints: DownloadConstraints
) : DownloadManager {
    override suspend fun enqueueChapter(mangaId: String, chapterId: String) {
        repository.createJob(chapterId)
        processQueue()
    }

    override fun observeQueue(): Flow<List<DownloadJob>> = repository.observeQueue()

    override suspend fun cancel(jobId: String) = repository.deleteJob(jobId)

    private suspend fun processQueue() {
        // Process one job at a time; WorkManager handles real background in Task 4.1
    }
}
```

- [ ] **Step 4: Write `DownloadConstraints.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/downloads/DownloadConstraints.kt
data class DownloadConstraints(
    val maxParallelPages: Int = 3,
    val minDelayMs: Long = 200,
    val maxDelayMs: Long = 800
)
```

- [ ] **Step 5: Write tests**

```kotlin
// core/src/test/java/com/opencapdown/core/downloads/DownloadManagerTest.kt
class DownloadManagerTest {
    @Test
    fun `enqueue creates queued job`() = runTest {
        val repository = mockk<DownloadRepository>(relaxed = true)
        val manager = DownloadManagerImpl(mockk(), mockk(), repository, File("/tmp"), DownloadConstraints())
        manager.enqueueChapter("m1", "c1")
        coVerify { repository.createJob("c1") }
    }
}
```

- [ ] **Step 6: Run tests**

```bash
./gradlew :core:testDebugUnitTest
```
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/opencapdown/core/downloads core/src/test/java/com/opencapdown/core/downloads
git commit -m "feat: add download manager with queue and image downloader"
```

---

## Task 5: Telegram Sync

**Files:**
- Create: `core/src/main/java/com/opencapdown/core/telegram/TelegramSync.kt`
- Create: `core/src/main/java/com/opencapdown/core/telegram/TelegramApiClient.kt`
- Create: `core/src/main/java/com/opencapdown/core/telegram/TelegramRateLimiter.kt`
- Create: `core/src/main/java/com/opencapdown/core/telegram/TelegramTopicManager.kt`
- Create: `core/src/main/java/com/opencapdown/core/telegram/TelegramMessageParser.kt`
- Create: `core/src/main/java/com/opencapdown/core/telegram/TelegramConfigProvider.kt`
- Test: `core/src/test/java/com/opencapdown/core/telegram/TelegramApiClientTest.kt` (uses MockWebServer)

**Interfaces:**
- Consumes: `OkHttpClient`, `SettingDao`, file cache, `LibraryMangaDao`, `ChapterDao`, `PageDao`.
- Produces: `TelegramSync.backupChapter`, `listBackups`, `restoreChapter`.

- [ ] **Step 1: Write `TelegramConfigProvider.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/telegram/TelegramConfigProvider.kt
interface TelegramConfigProvider {
    suspend fun getBotToken(): String?
    suspend fun getChatId(): String?
    suspend fun setConfig(botToken: String, chatId: String)
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/telegram/EncryptedTelegramConfigProvider.kt
internal class EncryptedTelegramConfigProvider(
    context: Context
) : TelegramConfigProvider {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "telegram_config",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun getBotToken(): String? = prefs.getString("bot_token", null)
    override suspend fun getChatId(): String? = prefs.getString("chat_id", null)
    override suspend fun setConfig(botToken: String, chatId: String) {
        prefs.edit().putString("bot_token", botToken).putString("chat_id", chatId).apply()
    }
}
```

- [ ] **Step 2: Write `TelegramApiClient.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/telegram/TelegramApiClient.kt
internal class TelegramApiClient(
    private val client: OkHttpClient,
    private val rateLimiter: TelegramRateLimiter
) {
    suspend fun createForumTopic(botToken: String, chatId: String, name: String): Int {
        val body = FormBody.Builder()
            .add("chat_id", chatId)
            .add("name", name)
            .build()
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$botToken/createForumTopic")
            .post(body)
            .build()
        return rateLimiter.run { execute(request) }.parseTopicId()
    }

    suspend fun sendMediaGroup(
        botToken: String,
        chatId: String,
        topicId: Int,
        media: List<TelegramInputMedia>
    ): List<TelegramMessage> {
        // multipart upload
    }

    suspend fun getFileUrl(botToken: String, fileId: String): String {
        // returns https://api.telegram.org/file/bot<token>/<file_path>
    }
}
```

- [ ] **Step 3: Write `TelegramSync.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/telegram/TelegramSync.kt
interface TelegramSync {
    suspend fun backupChapter(chapterId: String): Result<Unit>
    suspend fun listBackups(mangaId: String): List<TelegramBackup>
    suspend fun restoreChapter(messageId: Long): Result<Unit>
}
```

```kotlin
// core/src/main/java/com/opencapdown/core/telegram/TelegramSyncImpl.kt
internal class TelegramSyncImpl(...) : TelegramSync {
    override suspend fun backupChapter(chapterId: String): Result<Unit> {
        val token = config.getBotToken() ?: return Result.failure(IllegalStateException("Telegram not configured"))
        val chatId = config.getChatId() ?: return Result.failure(IllegalStateException("Telegram not configured"))
        // get chapter pages, create topic if needed, send media groups, persist fileIds
    }
}
```

- [ ] **Step 4: Write tests with MockWebServer**

```kotlin
// core/src/test/java/com/opencapdown/core/telegram/TelegramApiClientTest.kt
class TelegramApiClientTest {
    @Test
    fun `createForumTopic returns topic id`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{"message_thread_id":42}}"""))
        val client = TelegramApiClient(OkHttpClient(), TelegramRateLimiter())
        val id = client.createForumTopic("token", "123", "Manga Title")
        assertEquals(42, id)
        server.shutdown()
    }
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :core:testDebugUnitTest
```
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/opencapdown/core/telegram core/src/test/java/com/opencapdown/core/telegram
git commit -m "feat: add Telegram sync with encrypted config and rate limiting"
```

---

## Task 6: Reader Engine

**Files:**
- Create: `core/src/main/java/com/opencapdown/core/reader/ReaderEngine.kt`
- Create: `core/src/main/java/com/opencapdown/core/reader/PageResolver.kt`
- Create: `core/src/main/java/com/opencapdown/core/reader/ReadingProgressTracker.kt`
- Test: `core/src/test/java/com/opencapdown/core/reader/ReaderEngineTest.kt`

**Interfaces:**
- Consumes: `ChapterDao`, `PageDao`, `SourceManager`, `TelegramSync`.
- Produces: `ReaderEngine.getChapter`, `markAsRead`, `getProgress`.

- [ ] **Step 1: Write `PageResolver.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/reader/PageResolver.kt
internal class PageResolver(
    private val sourceManager: SourceManager,
    private val telegramSync: TelegramSync,
    private val cacheDir: File
) {
    suspend fun resolve(page: Page, chapterUrl: String): File {
        page.localPath?.let { return File(it) }
        page.telegramFileId?.let {
            telegramSync.restoreChapter(page.telegramMessageId!!).getOrThrow()
            return File(page.localPath!!)
        }
        // fallback: fetch from source
        val pages = sourceManager.getChapterPages("source", chapterUrl).getOrThrow()
        val target = pageUrl = pages[page.index].imageUrl
        // download to cache
    }
}
```

- [ ] **Step 2: Write `ReadingProgressTracker.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/reader/ReadingProgressTracker.kt
internal class ReadingProgressTracker(private val settingDao: SettingDao) {
    suspend fun save(mangaId: String, chapterId: String, pageIndex: Int) {
        settingDao.set(SettingEntity("progress:$mangaId", "$chapterId:$pageIndex"))
    }

    suspend fun load(mangaId: String): ReadingProgress? {
        return settingDao.get("progress:$mangaId")?.let {
            val (chapterId, pageIndex) = it.split(":")
            ReadingProgress(mangaId, chapterId, pageIndex.toInt())
        }
    }
}
```

- [ ] **Step 3: Write `ReaderEngine.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/reader/ReaderEngine.kt
interface ReaderEngine {
    suspend fun getChapter(chapterId: String): ChapterWithPages
    suspend fun markAsRead(chapterId: String)
    suspend fun getReadingProgress(mangaId: String): ReadingProgress?
    suspend fun updateProgress(mangaId: String, chapterId: String, pageIndex: Int)
}
```

- [ ] **Step 4: Write tests**

```kotlin
// core/src/test/java/com/opencapdown/core/reader/ReaderEngineTest.kt
class ReaderEngineTest {
    @Test
    fun `markAsRead updates chapter`() = runTest {
        val chapterDao = mockk<ChapterDao>(relaxed = true)
        val engine = ReaderEngineImpl(mockk(), chapterDao, mockk(), mockk(), mockk())
        engine.markAsRead("c1")
        coVerify { chapterDao.updateRead("c1", true) }
    }
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :core:testDebugUnitTest
```
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/opencapdown/core/reader core/src/test/java/com/opencapdown/core/reader
git commit -m "feat: add reader engine with progress tracking"
```

---

## Task 7: Core Facade and Public API

**Files:**
- Create: `core/src/main/java/com/opencapdown/core/OpenCapDownCore.kt`
- Create: `core/src/main/java/com/opencapdown/core/OpenCapDownCoreFactory.kt`
- Create: `core/src/main/java/com/opencapdown/core/di/CoreModule.kt`
- Create: `core/src/main/java/com/opencapdown/core/SourceContract.kt` (deprecated marker for future migrations)
- Test: `core/src/test/java/com/opencapdown/core/OpenCapDownCoreTest.kt`

**Interfaces:**
- Consumes: all previous modules.
- Produces: stable `OpenCapDownCore` facade.

- [ ] **Step 1: Write `OpenCapDownCore.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/OpenCapDownCore.kt
interface OpenCapDownCore {
    val version: String

    suspend fun search(query: String): List<SearchResult>
    suspend fun getMangaDetail(sourceId: String, mangaUrl: String): MangaDetail
    suspend fun getChapterPages(sourceId: String, chapterUrl: String): List<PageResult>

    suspend fun getLibrary(): List<LibraryManga>
    suspend fun addToLibrary(manga: MangaDetail)
    suspend fun removeFromLibrary(mangaId: String)

    suspend fun downloadChapter(mangaId: String, chapterId: String)
    fun observeDownloadQueue(): Flow<List<DownloadJob>>
    suspend fun cancelDownload(jobId: String)

    suspend fun backupChapter(chapterId: String): Result<Unit>
    suspend fun listTelegramBackups(mangaId: String): List<TelegramBackup>
    suspend fun restoreChapter(messageId: Long): Result<Unit>

    suspend fun getChapter(chapterId: String): ChapterWithPages
    suspend fun markAsRead(chapterId: String)
    suspend fun getReadingProgress(mangaId: String): ReadingProgress?
    suspend fun updateReadingProgress(mangaId: String, chapterId: String, pageIndex: Int)

    suspend fun getSettings(): Map<String, String>
    suspend fun updateTelegramConfig(botToken: String, chatId: String)
}
```

- [ ] **Step 2: Write `OpenCapDownCoreFactory.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/OpenCapDownCoreFactory.kt
object OpenCapDownCoreFactory {
    private const val VERSION = "1.0.0"

    fun create(context: Context): OpenCapDownCore {
        val coreModule = CoreModule(context)
        return coreModule.createCore(VERSION)
    }
}
```

- [ ] **Step 3: Write `CoreModule.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/di/CoreModule.kt
internal class CoreModule(private val context: Context) {
    private val database = DatabaseModule.provideDatabase(context)
    private val client = OkHttpClient.Builder().build()
    private val logger = AndroidLogger()

    fun createCore(version: String): OpenCapDownCore {
        val sourceManager = SourceManagerImpl(
            JsSourceLoader(context),
            { createSourceEngine() }
        )
        val downloadManager = DownloadManagerImpl(...)
        val telegramSync = TelegramSyncImpl(...)
        val readerEngine = ReaderEngineImpl(...)
        return OpenCapDownCoreImpl(
            version = version,
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            telegramSync = telegramSync,
            readerEngine = readerEngine,
            libraryRepository = LibraryRepositoryImpl(...)
        )
    }

    private fun createSourceEngine(): QuickJsSourceEngine {
        return QuickJsSourceEngine(
            HttpBridge(client),
            HtmlParserBridge(),
            logger
        )
    }
}
```

- [ ] **Step 4: Write `OpenCapDownCoreImpl.kt`**

```kotlin
// core/src/main/java/com/opencapdown/core/OpenCapDownCoreImpl.kt
internal class OpenCapDownCoreImpl(...) : OpenCapDownCore {
    override suspend fun search(query: String): List<SearchResult> {
        return sourceManager.listSources().flatMap { source ->
            when (val result = sourceManager.search(source.id, query)) {
                is OpenCapDownResult.Success -> result.data
                is OpenCapDownResult.Failure -> emptyList()
            }
        }
    }
    // delegate remaining methods
}
```

- [ ] **Step 5: Write tests**

```kotlin
// core/src/test/java/com/opencapdown/core/OpenCapDownCoreTest.kt
@RunWith(RobolectricTestRunner::class)
class OpenCapDownCoreTest {
    @Test
    fun `factory creates core with version`() {
        val core = OpenCapDownCoreFactory.create(ApplicationProvider.getApplicationContext())
        assertEquals("1.0.0", core.version)
    }
}
```

- [ ] **Step 6: Run tests**

```bash
./gradlew :core:testDebugUnitTest
```
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/opencapdown/core/OpenCapDownCore.kt core/src/main/java/com/opencapdown/core/OpenCapDownCoreFactory.kt core/src/main/java/com/opencapdown/core/di core/src/test/java/com/opencapdown/core/OpenCapDownCoreTest.kt
git commit -m "feat: add stable public OpenCapDownCore facade"
```

---

## Task 8: Documentation for AI and Final Verification

**Files:**
- Modify: `core/README.md`
- Create: `core/AI_GUIDE.md`
- Create: `app/AI_GUIDE.md`
- Create: `sources/README.md`
- Create: `docs/operations/api-contract.md`
- Create: `docs/operations/adding-a-source.md`
- Create: `docs/operations/testing-guide.md`
- Test: run full suite `./gradlew test`

**Interfaces:**
- Produces: documentation for future AI agents and humans.

- [ ] **Step 1: Write `core/AI_GUIDE.md`**

```markdown
# Guia para IA — Módulo `core`

## O que este módulo faz
Engine backend. Não contém UI.

## Regras de ouro
1. UI acessa apenas `OpenCapDownCore`.
2. Nunca quebre a interface `OpenCapDownCore`. Se precisar mudar, crie `OpenCapDownCore.v2()` e mantenha a v1.
3. Implementações ficam em pacotes `internal`.
4. Toda função pública precisa de teste.
5. Sources JS ficam em `sources/src/main/assets/sources/`.

## Onde colocar código novo
- Scraping → `sources/`
- Banco → `database/`
- Downloads → `downloads/`
- Telegram → `telegram/`
- Leitor → `reader/`

## Como testar
```bash
./gradlew :core:testDebugUnitTest
```
```

- [ ] **Step 2: Write `app/AI_GUIDE.md`**

```markdown
# Guia para IA — Módulo `app`

## Regras
- Este módulo é UI. Não faça scraping aqui.
- Use `OpenCapDownCoreFactory.create(context)` para obter o core.
- Todas as telas consomem `Flow` do core para dados reativos.
- Nunca acesse Room ou OkHttp diretamente.
```

- [ ] **Step 3: Write `docs/operations/api-contract.md`**

```markdown
# Contrato Público OpenCapDownCore

## Versão 1.0.0

Interface estável. Mudanças só via nova versão.

## Métodos
### search
- Input: `query: String`
- Output: `List<SearchResult>`
- Behavior: busca em todas as sources habilitadas

### downloadChapter
- Input: `mangaId: String, chapterId: String`
- Behavior: enfileira download. Notifica via `observeDownloadQueue()`.

### backupChapter
- Input: `chapterId: String`
- Output: `Result<Unit>`
- Behavior: envia capítulo pro Telegram configurado.
```

- [ ] **Step 4: Write `docs/operations/adding-a-source.md`**

```markdown
# Como adicionar uma source

1. Crie `sources/src/main/assets/sources/{id}.js`.
2. Implemente `search`, `getMangaDetail`, `getChapterPages`.
3. Adicione teste em `core/src/test/java/.../sources/`.
4. Rode `./gradlew :core:testDebugUnitTest`.
```

- [ ] **Step 5: Final verification**

```bash
./gradlew :core:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add core/README.md core/AI_GUIDE.md app/AI_GUIDE.md sources/README.md docs/operations
git commit -m "docs: add AI guides and API contract documentation"
```

---

## Self-Review Checklist

- [ ] Spec coverage: every section of the design spec has at least one task.
- [ ] Placeholder scan: no TBD/TODO/fill-in-details in task steps.
- [ ] Type consistency: `OpenCapDownCore` signatures match domain models and database entities.
- [ ] Test coverage: every public method has a test step.
- [ ] Scalability: modular packages, stable facade, no monolithic service.
- [ ] AI docs: README and AI_GUIDE explain module boundaries and contracts.

## Gaps to Address During Execution

1. **QuickJS/J2V8 dependency:** verify the exact artifact coordinates before Task 3. If `app.cash.quickjs:quickjs-android` does not exist, switch to `J2V8` (`com.eclipsesource.j2v8:j2v8_android`) and adjust `QuickJsSourceEngine`.
2. **WorkManager integration:** Task 4 creates a stub `DownloadWorker`. A follow-up task should wire WorkManager for background downloads.
3. **WebView bridge:** `WebViewBridge` is a stub. Heavy Cloudflare sources need a real implementation with `WebView.evaluateJavascript`.
4. **Telegram restore:** restore implementation must download file via `getFile` URL and save to cache.
5. **Source manifest:** add JSON manifest per source (`{id}.json`) for name, lang, icon, version if marketplace is needed later.
