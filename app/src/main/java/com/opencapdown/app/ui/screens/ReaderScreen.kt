package com.opencapdown.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.opencapdown.core.OpenCapDownCore
import com.opencapdown.core.domain.models.ChapterInfo
import com.opencapdown.core.domain.models.Page
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    core: OpenCapDownCore,
    sourceId: String,
    chapterUrl: String,
    chapterTitle: String,
    mangaUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Estados mutáveis para permitir navegação interna de capítulos
    var currentChapterUrl by remember { mutableStateOf(chapterUrl) }
    var currentChapterTitle by remember { mutableStateOf(chapterTitle) }

    var pages by remember { mutableStateOf<List<Page>?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showBars by remember { mutableStateOf(true) }
    var showIndexDialog by remember { mutableStateOf(false) }

    // Preferências locais do leitor
    var isInfiniteMode by remember { mutableStateOf(true) }
    var doubleClickRequired by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Carrega capítulos da obra para montar o índice e navegação N-1/N+1
    LaunchedEffect(mangaUrl) {
        try {
            val detail = core.getMangaDetail(sourceId, mangaUrl)
            chapters = detail.chapters
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val resolvedPages = pages ?: emptyList()

    // Estado do Pager para modo página por página (Infinito Inativo)
    val pagerState = rememberPagerState(pageCount = { resolvedPages.size })

    // Carrega páginas ao mudar de capítulo
    LaunchedEffect(currentChapterUrl) {
        isLoading = true
        try {
            val parts = currentChapterUrl.split("|")
            val chapterId = if (parts.size >= 3) parts[2] else currentChapterUrl
            val chapterWithPages = core.getChapter(chapterId)
            pages = chapterWithPages.pages.sortedBy { it.index }
            isLoading = false
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao carregar páginas: ${e.message}", Toast.LENGTH_LONG).show()
            isLoading = false
        }
    }

    // Garante reset de scroll e pagina para o inicio ou retoma progresso salvo de forma segura apos a renderizacao
    LaunchedEffect(pages) {
        val currentPages = pages
        if (currentPages != null) {
            val parts = currentChapterUrl.split("|")
            val mangaId = parts.firstOrNull() ?: ""
            val chId = if (parts.size >= 3) parts[2] else currentChapterUrl

            var targetPage = 0
            try {
                val progress = core.getReadingProgress(mangaId)
                if (progress != null && progress.chapterId == chId) {
                    targetPage = progress.pageIndex
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                listState.scrollToItem(targetPage)
            } catch (_: Exception) {}
            try {
                if (pagerState.pageCount > 0 && targetPage < pagerState.pageCount) {
                    pagerState.scrollToPage(targetPage)
                }
            } catch (_: Exception) {}
        }
    }

    // Calcula página atual para exibição na barra superior usando o estado observável 'pages' diretamente
    val currentPageIndex = remember {
        derivedStateOf {
            val currentPages = pages ?: emptyList()
            if (currentPages.isEmpty()) 0
            else if (isInfiniteMode) listState.firstVisibleItemIndex + 1
            else pagerState.currentPage + 1
        }
    }

    // Salva o progresso de leitura conforme o usuario rola/muda de pagina
    LaunchedEffect(currentPageIndex.value) {
        val pageIdx = currentPageIndex.value - 1
        if (pageIdx >= 0 && pages != null) {
            val parts = currentChapterUrl.split("|")
            val mangaId = parts.firstOrNull() ?: ""
            val chId = if (parts.size >= 3) parts[2] else currentChapterUrl
            if (mangaId.isNotEmpty() && chId.isNotEmpty()) {
                try {
                    core.updateReadingProgress(mangaId, chId, pageIdx)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Navegação de capítulos
    val currentChapterIdx = chapters.indexOfFirst { it.url == currentChapterUrl }
    val hasPrevChapter = currentChapterIdx != -1 && currentChapterIdx < chapters.size - 1 // Capítulo antigo
    val hasNextChapter = currentChapterIdx != -1 && currentChapterIdx > 0 // Capítulo novo

    fun navigateToPrevChapter() {
        if (hasPrevChapter) {
            val prev = chapters[currentChapterIdx + 1]
            currentChapterUrl = prev.url
            currentChapterTitle = prev.title
        }
    }

    fun navigateToNextChapter() {
        if (hasNextChapter) {
            val next = chapters[currentChapterIdx - 1]
            currentChapterUrl = next.url
            currentChapterTitle = next.title
        }
    }

    // Modificador de gestos dinâmico conforme o toggle de bloqueio de cliques
    val gestureModifier = Modifier.pointerInput(doubleClickRequired) {
        detectTapGestures(
            onTap = {
                if (!doubleClickRequired) {
                    showBars = !showBars
                }
            },
            onDoubleTap = {
                if (doubleClickRequired) {
                    showBars = !showBars
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (isInfiniteMode) {
                // Modo Rolagem Contínua (Webtoon)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(gestureModifier)
                ) {
                    itemsIndexed(resolvedPages) { _, page ->
                        ReaderPageItem(
                            core = core,
                            page = page,
                            sourceId = sourceId,
                            chapterUrl = currentChapterUrl,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                // Modo Página por Página (Infinito Inativo - economiza memória)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(gestureModifier)
                ) { pageIndex ->
                    val page = resolvedPages[pageIndex]
                    ReaderPageSingle(
                        core = core,
                        page = page,
                        sourceId = sourceId,
                        chapterUrl = currentChapterUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // HUD de Navegação centralizado lateralmente nas imagens (mostrado quando os menus estão visíveis)
        AnimatedVisibility(
            visible = showBars && !isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Seta Capítulo Anterior (Esquerda)
                if (hasPrevChapter) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(16.dp)
                    ) {
                        IconButton(
                            onClick = { navigateToPrevChapter() },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Anterior", tint = Color.White)
                        }
                    }
                }

                // Seta Próximo Capítulo (Direita)
                if (hasNextChapter) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(16.dp)
                    ) {
                        IconButton(
                            onClick = { navigateToNextChapter() },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Próximo", tint = Color.White)
                        }
                    }
                }

                // Painel Flutuante Inferior com Controles de Posição
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Seta Começo do Capítulo (Cima)
                        IconButton(onClick = {
                            scope.launch {
                                if (isInfiniteMode) listState.animateScrollToItem(0)
                                else pagerState.animateScrollToPage(0)
                            }
                        }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Começo", tint = Color.White)
                        }

                        // Botão de Índice (Pop-up)
                        IconButton(onClick = { showIndexDialog = true }) {
                            Icon(Icons.Default.List, contentDescription = "Índice", tint = Color.White)
                        }

                        // Seta Fim do Capítulo (Baixo)
                        IconButton(onClick = {
                            scope.launch {
                                val lastIndex = resolvedPages.size - 1
                                if (lastIndex >= 0) {
                                    if (isInfiniteMode) listState.animateScrollToItem(lastIndex)
                                    else pagerState.animateScrollToPage(lastIndex)
                                }
                            }
                        }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Fim", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Overlay do TopAppBar com opções e modos extras
        AnimatedVisibility(
            visible = showBars && !isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentChapterTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Página ${currentPageIndex.value} de ${resolvedPages.size}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    // Toggle Modo Infinito (Ícone de Infinito)
                    IconButton(onClick = { isInfiniteMode = !isInfiniteMode }) {
                        Icon(
                            imageVector = if (isInfiniteMode) Icons.Default.AllInclusive else Icons.Default.Brightness1,
                            contentDescription = "Alternar Modo de Leitura",
                            tint = if (isInfiniteMode) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }

                    // Toggle Bloqueio de Clique (Ícone de Olho)
                    IconButton(onClick = { doubleClickRequired = !doubleClickRequired }) {
                        Icon(
                            imageVector = if (doubleClickRequired) Icons.Default.RemoveRedEye else Icons.Default.VisibilityOff,
                            contentDescription = "Alternar Bloqueio de Clique",
                            tint = if (doubleClickRequired) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.85f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    }

    // Pop-up do Índice de Capítulos (Fecha ao clicar fora)
    if (showIndexDialog) {
        AlertDialog(
            onDismissRequest = { showIndexDialog = false },
            title = { Text("Índice de Capítulos", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.height(350.dp)) {
                    LazyColumn {
                        itemsIndexed(chapters) { _, ch ->
                            val isCurrent = ch.url == currentChapterUrl
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showIndexDialog = false
                                        currentChapterUrl = ch.url
                                        currentChapterTitle = ch.title
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = ch.title,
                                    fontSize = 16.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIndexDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Composable
fun ReaderPageItem(
    core: OpenCapDownCore,
    page: Page,
    sourceId: String,
    chapterUrl: String,
    modifier: Modifier = Modifier
) {
    var resolvedModel by remember(page, chapterUrl) { mutableStateOf<Any?>(null) }
    var isLoading by remember(page, chapterUrl) { mutableStateOf(true) }
    var hasError by remember(page, chapterUrl) { mutableStateOf(false) }

    LaunchedEffect(page, chapterUrl) {
        isLoading = true
        hasError = false
        try {
            resolvedModel = core.resolvePage(page, sourceId, chapterUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            hasError = true
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = modifier.heightIn(min = 360.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        } else if (hasError) {
            Text("Erro ao carregar página", color = Color.Red, fontSize = 12.sp)
        } else {
            AsyncImage(
                model = resolvedModel,
                contentDescription = "Página ${page.index + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            )
        }
    }
}

@Composable
fun ReaderPageSingle(
    core: OpenCapDownCore,
    page: Page,
    sourceId: String,
    chapterUrl: String,
    modifier: Modifier = Modifier
) {
    var resolvedModel by remember(page, chapterUrl) { mutableStateOf<Any?>(null) }
    var isLoading by remember(page, chapterUrl) { mutableStateOf(true) }
    var hasError by remember(page, chapterUrl) { mutableStateOf(false) }

    LaunchedEffect(page, chapterUrl) {
        isLoading = true
        hasError = false
        try {
            resolvedModel = core.resolvePage(page, sourceId, chapterUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            hasError = true
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        } else if (hasError) {
            Text("Erro ao carregar página", color = Color.Red, fontSize = 14.sp)
        } else {
            AsyncImage(
                model = resolvedModel,
                contentDescription = "Página ${page.index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
