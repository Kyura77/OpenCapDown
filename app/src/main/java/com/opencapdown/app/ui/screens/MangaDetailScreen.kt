package com.opencapdown.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.opencapdown.core.OpenCapDownCore
import com.opencapdown.core.domain.models.MangaDetail
import com.opencapdown.core.domain.models.LibraryManga
import com.opencapdown.core.domain.models.ReadingProgress
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailScreen(
    core: OpenCapDownCore,
    sourceId: String,
    mangaUrl: String,
    onBack: () -> Unit,
    onChapterClick: (chapterUrl: String, chapterTitle: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mangaDetail by remember { mutableStateOf<MangaDetail?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf<List<LibraryManga>>(emptyList()) }
    val isBackingUpMap = remember { mutableStateMapOf<String, Boolean>() }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    val mangaId = mangaDetail?.let { "${it.sourceId}-${it.title.hashCode()}" } ?: ""

    var cachedManga by remember { mutableStateOf<LibraryManga?>(null) }
    var readingProgress by remember { mutableStateOf<ReadingProgress?>(null) }

    val effectiveMangaId = mangaDetail?.let { "${it.sourceId}-${it.title.hashCode()}" } ?: cachedManga?.id ?: ""

    // Observa o ciclo de vida para recarregar o progresso sempre que o usuario voltar do leitor
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, effectiveMangaId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (effectiveMangaId.isNotEmpty()) {
                    scope.launch {
                        try {
                            readingProgress = core.getReadingProgress(effectiveMangaId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Atualiza status da biblioteca
    val refreshLibraryStatus = suspend {
        val lib = core.getLibrary()
        library = lib
        val currentMangaId = mangaDetail?.let { "${it.sourceId}-${it.title.hashCode()}" } ?: mangaId
        if (currentMangaId.isNotEmpty()) {
            isFavorite = lib.any { it.mangaUrl == mangaUrl || it.id == currentMangaId }
        }
    }

    LaunchedEffect(Unit) {
        // Carrega cache local instantaneamente se houver
        try {
            val lib = core.getLibrary()
            library = lib
            val local = lib.firstOrNull { it.mangaUrl == mangaUrl }
            if (local != null) {
                cachedManga = local
                isFavorite = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Busca detalhes e capitulos da rede em segundo plano
        try {
            mangaDetail = core.getMangaDetail(sourceId, mangaUrl)
            refreshLibraryStatus()
        } catch (e: Exception) {
            if (cachedManga == null) {
                Toast.makeText(context, "Erro ao carregar detalhes: ${e.message}", Toast.LENGTH_LONG).show()
                onBack()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val detail = mangaDetail
        val displayTitle = detail?.title ?: cachedManga?.title ?: "Carregando..."
        val displayCoverUrl = detail?.coverUrl ?: cachedManga?.coverUrl ?: ""
        val displayStatus = detail?.status ?: cachedManga?.status ?: ""

        if (detail == null && cachedManga == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Imersivo do Mangá (Visual do Mihon)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) {
                        // Fundo de cor escura gradiente simples e leve (consome 0 memoria)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )

                        // Conteúdo sobreposto do cabeçalho
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Capa Real flutuando com sombra
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                modifier = Modifier
                                    .width(110.dp)
                                    .aspectRatio(0.72f)
                            ) {
                                AsyncImage(
                                    model = displayCoverUrl,
                                    contentDescription = displayTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Título, status e autor
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = displayTitle,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Fonte: ${sourceId.uppercase()}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                if (displayStatus.isNotEmpty()) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(displayStatus, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        ),
                                        border = null
                                    )
                                }
                            }
                        }
                    }
                }

                // Botões de Ações Rápidas (Favoritar, Compartilhar/Backup Geral)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    if (isFavorite) {
                                        core.removeFromLibrary(mangaId)
                                        Toast.makeText(context, "Removido dos favoritos", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val currentDetail = detail ?: MangaDetail(
                                            sourceId = sourceId,
                                            url = mangaUrl,
                                            title = displayTitle,
                                            coverUrl = displayCoverUrl,
                                            description = "",
                                            status = displayStatus,
                                            chapters = emptyList()
                                        )
                                        core.addToLibrary(currentDetail)
                                        Toast.makeText(context, "Adicionado aos favoritos", Toast.LENGTH_SHORT).show()
                                    }
                                    refreshLibraryStatus()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFavorite) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                                contentColor = if (isFavorite) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isFavorite) "Favoritado" else "Favoritar", fontWeight = FontWeight.Bold)
                        }

                        // Botão Ler / Continuar Capítulo
                        if (detail != null && detail.chapters.isNotEmpty()) {
                            val progress = readingProgress
                            val targetChapter = if (progress != null) {
                                detail.chapters.firstOrNull { it.id == progress.chapterId }
                            } else null

                            val buttonText = if (targetChapter != null) "Continuar" else "Ler Início"
                            val chToOpen = targetChapter ?: detail.chapters.last() // last() representa o primeiro capitulo lançado

                            Button(
                                onClick = {
                                    onChapterClick(chToOpen.url, chToOpen.title)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(buttonText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Sinopse Expansível ("Ler mais" estilo Mihon)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .animateContentSize()
                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                    ) {
                        Text(
                            text = "Sinopse",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = detail?.description?.ifEmpty { "Sem sinopse disponível." } ?: "Carregando sinopse...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 19.sp,
                            maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isDescriptionExpanded) "Ler menos" else "Ler mais...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                // Seção dos Capítulos
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (detail != null) "Capítulos (${detail.chapters.size})" else "Capítulos (Carregando...)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Lista de Capítulos ou Spinner Progressivo
                if (detail == null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        }
                    }
                } else {
                    itemsIndexed(detail.chapters) { _, chapter ->
                        val isBackingUp = isBackingUpMap[chapter.id] == true

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChapterClick(chapter.url, chapter.title) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chapter.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Nº ${chapter.number}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Botão Baixar Offline
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            if (!isFavorite) {
                                                core.addToLibrary(detail)
                                                refreshLibraryStatus()
                                            }
                                            core.downloadChapter(mangaId, chapter.id)
                                            Toast.makeText(context, "Download iniciado", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Baixar",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Botão Backup Telegram
                                if (isBackingUp) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                isBackingUpMap[chapter.id] = true
                                                val result = core.backupChapter(chapter.id)
                                                isBackingUpMap[chapter.id] = false
                                                if (result.isSuccess) {
                                                    Toast.makeText(context, "Backup enviado para o Telegram!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Falha no backup: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Backup Telegram",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    }
                }
            }
        }

        // Botão de Voltar Flutuante Translúcido (Mihon)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .align(Alignment.TopStart)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
        }
    }
}
