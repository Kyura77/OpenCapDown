# Prompt: Agente de UI — OpenCapDown

## Sua missão

Construir o módulo `app/` do OpenCapDown — um app Android nativo de leitura de mangá usando Jetpack Compose + Material 3 + Coil. Você recebe o `core/` pronto (engine de scraping, banco, downloads, sync Telegram). Seu trabalho é **apenas UI**.

---

## Como o core funciona

Uma classe `OpenCapDownCore` é a **única porta de entrada**. Você nunca acessa Room, OkHttp, QuickJS ou Telegram diretamente.

```kotlin
// No Application ou Activity:
val core = OpenCapDownCoreFactory.create(applicationContext)
```

Toda chamada é `suspend fun` ou `Flow`. Você chama do `ViewModel` via `viewModelScope.launch { }`.

---

## Domain Models (o que trafega entre core e UI)

```kotlin
data class LibraryManga(
    val id: String, val sourceId: String, val title: String,
    val coverUrl: String, val status: String,
    val telegramTopicId: Int? = null
)

data class SearchResult(
    val sourceId: String, val title: String,
    val coverUrl: String, val url: String
)

data class MangaDetail(
    val sourceId: String, val title: String, val coverUrl: String,
    val description: String, val status: String,
    val chapters: List<ChapterInfo>
)

data class ChapterInfo(
    val id: String, val title: String, val url: String, val number: Float
)

data class ChapterWithPages(
    val chapter: Chapter, val pages: List<Page>
)

data class Chapter(
    val id: String, val mangaId: String, val number: Float,
    val title: String, val pageCount: Int,
    val integrityStatus: IntegrityStatus, // PENDING, COMPLETE, PARTIAL, BACKED_UP
    val telegramAlbumMessageId: Long? = null, val isRead: Boolean = false
)

data class Page(
    val id: String, val chapterId: String, val index: Int,
    val localPath: String? = null,
    val telegramFileId: String? = null, val telegramMessageId: Long? = null
)

data class PageResult(
    val index: Int, val imageUrl: String,
    val headers: Map<String, String> = emptyMap()
)

data class ReadingProgress(
    val mangaId: String, val chapterId: String, val pageIndex: Int
)

data class TelegramBackup(
    val messageId: Long, val chapterTitle: String,
    val pageCount: Int, val createdAt: Long
)

data class DownloadJob(
    val id: String, val chapterId: String,
    val status: DownloadStatus, // QUEUED, DOWNLOADING, COMPLETE, FAILED, CANCELLED
    val progress: Int = 0, val errorMessage: String? = null
)
```

---

## Contrato Completo do OpenCapDownCore

```kotlin
interface OpenCapDownCore {
    val version: String

    // Sources
    suspend fun search(query: String): List<SearchResult>
    suspend fun getMangaDetail(sourceId: String, mangaUrl: String): MangaDetail
    suspend fun getChapterPages(sourceId: String, chapterUrl: String): List<PageResult>

    // Biblioteca
    suspend fun getLibrary(): List<LibraryManga>
    suspend fun addToLibrary(manga: MangaDetail)
    suspend fun removeFromLibrary(mangaId: String)

    // Downloads
    suspend fun downloadChapter(mangaId: String, chapterId: String)
    fun observeDownloadQueue(): Flow<List<DownloadJob>>
    suspend fun cancelDownload(jobId: String)

    // Telegram
    suspend fun backupChapter(chapterId: String): Result<Unit>
    suspend fun listTelegramBackups(mangaId: String): List<TelegramBackup>
    suspend fun restoreChapter(messageId: Long): Result<Unit>

    // Leitor
    suspend fun getChapter(chapterId: String): ChapterWithPages
    suspend fun markAsRead(chapterId: String)
    suspend fun getReadingProgress(mangaId: String): ReadingProgress?
    suspend fun updateReadingProgress(mangaId: String, chapterId: String, pageIndex: Int)

    // Config
    suspend fun getSettings(): Map<String, String>
    suspend fun updateTelegramConfig(botToken: String, chatId: String)
}
```

---

## Telas Obrigatórias

### 1. Tela de Biblioteca (Home)
- Grid de mangás salvos (2 colunas) com capa + título + indicador de não-lido.
- Estado vazio: "Sua biblioteca está vazia. Toque na lupa para adicionar."
- Pull-to-refresh.
- FAB leva à busca.
- Cada card: clique → tela de detalhes, long-press → menu remover.

### 2. Tela de Busca
- SearchBar no topo com debounce (~400ms).
- Resultados em grid igual à biblioteca.
- Cada resultado: capa + título + badge da source.
- Clique → tela de detalhes.
- Tratar loading, vazio, erro.

### 3. Tela de Detalhes do Mangá
- Header: capa grande, título, status, descrição expansível.
- "Adicionar à Biblioteca" / "Remover" (toggle).
- Lista de capítulos ordenada por número (decrescente ou crescente, toggle).
- Cada capítulo: número + título + badge de status (lido, baixado, backed up, pendente).
- Ações no capítulo:
  - Tap → abrir leitor.
  - Long-press → menu: download, backup Telegram, marcar lido.
- Indicador de progresso de leitura no capítulo atual.

### 4. Tela de Leitura (átomo da UX)
- Viewer em scrolling vertical contínuo (Webtoon-style) — padrão.
- Páginas carregadas com lazy loading + placeholder shimmer.
- Modo página-simples (horizontal swipe) como alternativa, acessível por toggle.
- Suporte a zoom (pinch-to-zoom com double-tap reset).
- Ao chegar no fim: popup "Próximo capítulo?".
- Salva progresso a cada página virada.
- Overlay ao tocar: top bar (título, voltar, config) + bottom bar (slider de páginas, toggle modo).
- Auto-hide do overlay após 2s.

### 5. Tela de Downloads
- Lista de jobs ativos: barra de progresso + nome + botão cancelar.
- Aba "Completos": capítulos baixados, swipe para deletar cache.

### 6. Tela de Configurações
- Campos: Bot Token, Chat ID (Telegram).
- Status de conexão (verificado/não configurado/erro).
- Versão do core.
- Tamanho do cache e botão limpar.

---

## Regras de UX (leia com atenção)

### Navegação
- Bottom Navigation com 3 abas: Biblioteca | Downloads | Config.
- Search acessível via FAB na Biblioteca ou ícone na top bar.
- Detalhes e leitor são pushes na pilha (sem bottom nav visível).

### Estados
Toda tela com dados remotos deve tratar 4 estados:
| Estado | UI |
|--------|-----|
| Loading | Shimmer placeholder (nunca spinner genérico) |
| Success | Conteúdo real |
| Empty | Ilustração + texto explicativo + call-to-action |
| Error | Mensagem amigável + botão "Tentar novamente" + toast com detalhe |

### Animações e Micro-Interações
- Transições de tela: fade + slide sutis (padrão do Compose Navigation).
- Cards da biblioteca: spring scale 0.97 ao pressionar.
- Shimmer loading: gradiente animado (uso da biblioteca accompanist ou Compose Shimmer).
- Overlay no leitor: fade in/out suave, não aparece em toques acidentais (hit area fina no topo).
- Toast/Snackbar: não use — use Indicador próprio minimalista (ex: texto pequeno no topo que some).

### Leituras e Acessibilidade
- Conteúdo deve ser legível tanto em dark mode quanto light mode.
- Contraste mínimo 4.5:1 em texto normal.
- Touch targets >= 48dp (especialmente botões de capítulo e navegação).
- Texto escala com configuração de fonte do sistema (use `MaterialTheme.typography`, nunca `sp` fixo).
- Suporte a RTL se o sistema estiver configurado.

### Performance
- Capas: `AsyncImage` do Coil com `crossfade(300)` e `size(400)`.
- Páginas do leitor: `AsyncImage` com `size(1200)` em width, `MemoryCacheKey` por URL.
- Lazy vertical grid/staggered grid para Library e Search — nunca `Column` com todos os itens.
- ViewModel salva estado em `SavedStateHandle` para sobreviver a rotação e recriação.

---

## Stack Técnica

| Camada | Tecnologia |
|--------|-----------|
| UI | Jetpack Compose + Material 3 (androidx.compose.material3) |
| Navegação | Navigation Compose (type-safe) |
| Imagens | Coil 3.x (`AsyncImage`) |
| ViewModel | AndroidX ViewModel + SavedStateHandle |
| DI | Manual via Application (sem Hilt/Koin) |
| Testes | Compose UI Test + Paparazzi (snapshot) |
| Cores/temas | Material You + `dynamicColor = true` |

## Tema e Design Tokens

```kotlin
// Sempre usar MaterialTheme.colorScheme e MaterialTheme.typography
// Se dynamicColor disponível (API 31+), usar como padrão.
// Fallback: paleta dark/light manual com:
//   primary = #3A86FF, secondary = #8338EC, tertiary = #FF006E
// Superfície: Dark = #121212, Light = #F8F9FA
```

- **Headers e títulos**: `titleLarge` ou `headlineSmall`.
- **Números/capítulos**: `bodyMedium` com `fontFeatureSettings = "tnum"` (tabular numbers).
- **Descrições**: `bodySmall` com line height 1.5.
- **Badges**: `labelSmall` com container arredondado.
- **Card corners**: `RoundedCornerShape(12.dp)`, imagens das capas `RoundedCornerShape(8.dp)`.
- **Elevação**: cards 2.dp, FAB 6.dp, bottom nav 8.dp.

---

## Estrutura de Arquivos Esperada

```
app/src/main/java/com/opencapdown/app/
├── OpenCapDownApp.kt              # Application: inicializa core + DI
├── MainActivity.kt                # Single Activity, setContent com NavHost
├── navigation/
│   ├── Screen.kt                  # sealed class de rotas
│   └── NavGraph.kt                # NavHost com todas as rotas
├── ui/
│   ├── theme/
│   │   ├── Theme.kt               # OpenCapDownTheme com dynamicColor + fallback
│   │   ├── Color.kt               # Paletas light/dark
│   │   └── Type.kt                # Tipografia (se precisar customizar)
│   ├── library/
│   │   ├── LibraryScreen.kt       # Grid + pull-refresh + empty state
│   │   └── LibraryViewModel.kt
│   ├── search/
│   │   ├── SearchScreen.kt        # SearchBar + resultados + debounce
│   │   └── SearchViewModel.kt
│   ├── detail/
│   │   ├── DetailScreen.kt        # Header + capítulos + ações
│   │   └── DetailViewModel.kt
│   ├── reader/
│   │   ├── ReaderScreen.kt        # Viewer vertical/horizontal + overlay
│   │   └── ReaderViewModel.kt
│   ├── downloads/
│   │   ├── DownloadsScreen.kt     # Abas: ativos + completos
│   │   └── DownloadsViewModel.kt
│   └── settings/
│       ├── SettingsScreen.kt      # Config Telegram + cache + versão
│       └── SettingsViewModel.kt
└── di/
    └── AppModule.kt               # Singletons: core, dispatchers
```

---

## Regras Finais

1. Cada arquivo de tela deve ter um comentário no topo com seu propósito e fluxo de dados.
2. Nunca importe nada do pacote `com.opencapdown.core` que não seja `OpenCapDownCore`, `OpenCapDownCoreFactory` ou os models de domínio.
3. Cada ViewModel deve ter teste unitário mockando o core.
4. Toda string deve estar em `strings.xml` para suporte a i18n futuro.
5. Teste de UI: ao menos um teste por tela com `ComposeTestRule`.
6. Dark mode obrigatório. Testar nos dois temas.

---

**Ao terminar, faça commit com a mensagem: `feat: add full Compose UI with reader, search, library, downloads, and settings`**
