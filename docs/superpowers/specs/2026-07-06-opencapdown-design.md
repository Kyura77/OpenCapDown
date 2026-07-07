# OpenCapDown — Especificação de Design

## 1. Objetivo

Aplicativo Android para leitura de mangá a partir de fontes web. Armazena capítulos no Telegram como backup gratuito e permite leitura offline com leitor próprio. Sem servidor externo, sem Termux, sem infraestrutura paga.

## 2. Contexto

Projeto anterior (CapDown) já provou:
- Web scraping funciona para Verdinha, MangaDex, EgoToons (Madara CMS).
- Telegram Bot API armazena páginas como álbuns via `sendMediaGroup`.
- Fórum topics do Telegram organizam por mangá.

Problemas do anterior:
- Precisava de API Node.js + scraper Python + Redis rodando no Termux.
- Muito pesado para manter no celular.
- `ProductStateService` monolítico (915 linhas).

Este projeto resolve isso colocando tudo no app Android nativo.

## 3. Decisões de Arquitetura

| Decisão | Escolha | Motivo |
|---------|---------|--------|
| Plataforma | Android nativo (Kotlin) | Melhor performance, acesso nativo a storage, notificações, background. |
| Sources | JavaScript carregado em runtime via QuickJS | Adicionar fonte sem compilar APK. Reutiliza lógica do Haruneko e Seanime. |
| Storage primário | Telegram Bot API | Nuvem pessoal gratuita, acessível de qualquer dispositivo. |
| Storage local | Room (SQLite) + cache de imagens | Metadados, fila de downloads, leitura offline. |
| Leitor | Próprio (UI por outra IA) | Controle total de UX: zoom, scroll, modos, progresso. |
| Infraestrutura | Zero servidor | App funciona sozinho. Telegram é só backend de storage. |

## 4. Componentes do Core

```
UI Layer (Compose/XML — outra IA)
    │
    ▼
CoreAPI (interfaces Kotlin consumidas pela UI)
    │
    ├── SourceManager
    │       ├── Loader de sources JS (assets/sources/*.js)
    │       └── QuickJS runtime
    │
    ├── DownloadManager
    │       ├── Fila de downloads
    │       ├── Cache local de imagens
    │       └── Notificações de progresso
    │
    ├── TelegramSync
    │       ├── upload de capítulos (sendMediaGroup)
    │       ├── restore de capítulos
    │       └── rate-limit handler
    │
    ├── ReaderEngine
    │       ├── Cache hit = imagem local
    │       ├── Cache miss = download da source
    │       └── Progresso de leitura
    │
    └── LocalDB (Room)
            ├── LibraryManga
            ├── Chapter
            ├── Page
            ├── DownloadJob
            └── Settings
```

## 5. Contrato CoreAPI

Interface Kotlin exposta para a camada de UI.

```kotlin
interface OpenCapDownCore {

    // Sources
    suspend fun search(query: String): List<SearchResult>
    suspend fun getMangaDetail(sourceId: String, mangaUrl: String): MangaDetail
    suspend fun getChapterPages(sourceId: String, chapterUrl: String): List<PageResult>

    // Biblioteca local
    suspend fun getLibrary(): List<LibraryManga>
    suspend fun addToLibrary(manga: MangaDetail)
    suspend fun removeFromLibrary(mangaId: String)

    // Downloads
    suspend fun downloadChapter(mangaId: String, chapterId: String)
    suspend fun getDownloadQueue(): Flow<List<DownloadJob>>
    suspend fun cancelDownload(jobId: String)

    // Telegram
    suspend fun backupChapter(chapterId: String): Result<Unit>
    suspend fun listTelegramBackups(): List<TelegramBackup>
    suspend fun restoreChapter(telegramMessageId: String): Result<Unit>

    // Leitor
    suspend fun getChapter(chapterId: String): ChapterWithPages
    suspend fun markAsRead(chapterId: String)
    suspend fun getReadingProgress(mangaId: String): ReadingProgress

    // Configurações
    suspend fun getSettings(): Settings
    suspend fun updateTelegramConfig(botToken: String, chatId: String)
}
```

### 5.1 Tipos de retorno

```kotlin
data class SearchResult(
    val sourceId: String,
    val title: String,
    val coverUrl: String,
    val url: String
)

data class MangaDetail(
    val sourceId: String,
    val title: String,
    val coverUrl: String,
    val description: String,
    val status: String,
    val chapters: List<ChapterInfo>
)

data class ChapterInfo(
    val id: String, // gerado localmente: sourceId + hash(url)
    val title: String,
    val url: String,
    val number: Float
)

data class PageResult(
    val index: Int,
    val imageUrl: String,
    val headers: Map<String, String> = emptyMap()
)

data class ChapterWithPages(
    val chapter: Chapter,
    val pages: List<Page>
)

data class ReadingProgress(
    val mangaId: String,
    val chapterId: String,
    val pageIndex: Int
)

data class TelegramBackup(
    val messageId: Long,
    val chapterTitle: String,
    val pageCount: Int,
    val createdAt: Long
)
```

## 6. Modelo de Dados (Room)

```kotlin
@Entity
data class LibraryManga(
    @PrimaryKey val id: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String,
    val status: String,
    val telegramTopicId: Int? = null
)

@Entity
data class Chapter(
    @PrimaryKey val id: String,
    val mangaId: String,
    val number: Float,
    val title: String,
    val pageCount: Int,
    val integrityStatus: String, // pending | complete | partial | backed_up
    val telegramAlbumMessageId: Long? = null,
    val isRead: Boolean = false
)

@Entity
data class Page(
    @PrimaryKey val id: String,
    val chapterId: String,
    val index: Int,
    val localPath: String? = null,
    val telegramFileId: String? = null,
    val telegramMessageId: Long? = null
)

@Entity
data class DownloadJob(
    @PrimaryKey val id: String,
    val chapterId: String,
    val status: String, // queued | downloading | complete | failed | cancelled
    val progress: Int = 0,
    val errorMessage: String? = null
)

@Entity
data class Settings(
    @PrimaryKey val key: String,
    val value: String
)
```

## 7. Sistema de Sources

Cada source é um arquivo JavaScript em `assets/sources/{id}.js`.

```javascript
export default {
  id: "verdinha",
  name: "Verdinha",
  lang: "pt-BR",
  baseUrl: "https://verdinha.com",

  async search(query) {
    // retorna: [{ title, coverUrl, url }]
  },

  async getMangaDetail(url) {
    // retorna: { title, coverUrl, description, chapters: [{ title, url, number }] }
  },

  async getChapterPages(url) {
    // retorna: ["https://cdn.../01.jpg", "https://cdn.../02.jpg"]
  }
}
```

O `SourceManager` injeta helpers no runtime JS:
- `fetch(url, options)` — HTTP com headers customizáveis.
- `parseHtml(html)` — parser DOM leve.
- `log(level, message)` — logcat.

## 8. Fluxo de Download

1. UI chama `downloadChapter(mangaId, chapterId)`.
2. `DownloadManager` cria `DownloadJob` com status `queued`.
3. Worker executa:
   - Busca URLs das páginas via `SourceManager.getChapterPages()`.
   - Baixa imagens em paralelo (semaphore limitado).
   - Salva em `cache/chapters/{chapterId}/{index}.jpg`.
   - Atualiza `Page.localPath`.
   - Marca `Chapter.integrityStatus = complete`.
4. Se usuário optou por backup Telegram:
   - Agrupa páginas em lotes de até 10.
   - Chama `sendMediaGroup` no tópico do mangá.
   - Salva `telegramFileId` e `telegramMessageId` em cada `Page`.

## 9. Fluxo de Leitura

1. UI abre capítulo.
2. `ReaderEngine` retorna lista de páginas.
3. Para cada página:
   - Se `localPath` existe → carrega do disco.
   - Senão, se `telegramFileId` existe → baixa do Telegram.
   - Senão → baixa da source em tempo real.
4. Progresso de leitura salvo a cada página.

## 10. Fluxo Telegram

### Backup
```
ensureTopic(manga)
  -> createForumTopic(chatId, manga.title)
  -> salva telegramTopicId em LibraryManga

uploadChapter(chapter)
  -> para cada 10 páginas: sendMediaGroup(topicId, imagens)
  -> persiste fileId/messageId
```

### Restore
```
listBackups()
  -> busca mensagens no tópico
  -> retorna capítulos disponíveis

restoreChapter(messageId)
  -> getFile(fileId)
  -> download para cache local
  -> atualiza Page.localPath
```

## 11. Segurança

- Token do bot e chatId salvos em `EncryptedSharedPreferences`, nunca plaintext.
- Cookies de login de fontes VIP salvos no KeyStore/encrypted storage.
- Nenhuma credencial hardcoded no código.

## 12. Anti-Bot e Headers

- User-Agent sincronizado entre source, downloader e Telegram.
- Delay aleatório entre requisições.
- Reutilização de sessão HTTP (connection pooling via OkHttp).
- Cookies persistentes por source.
- Para proteções leves (headers, referrer): source JS controla via helper `fetch()` que usa OkHttp nativo.
- Para desafios JavaScript/Cloudflare pesados: `SourceManager` expõe `fetchViaWebView(url)` que executa num WebView headless invisível e retorna HTML final. Source JS pode optar por esse modo quando necessário.

## 13. Limitações Conhecidas

- Telegram: upload máximo de 20 MB por foto, 10 fotos por `sendMediaGroup`.
- QuickJS: não roda DOM nativo; fontes devem fazer parsing manual ou via helper injetado.
- Android: background downloads podem ser limitados pelo sistema (WorkManager obrigatório).

## 14. Referências

- **Haruneko**: runtime JS para sources.
- **Tsukimi**: leitor Android, gerenciamento de downloads, anti-bot.
- **extensions-source (keiyoushi)**: parsers e lógica de sites.
- **seanime-provider**: marketplace de providers, manifestos.
- **CapDown (legado)**: lógica de providers Verdinha/Madara/MangaDex e integração Telegram.
