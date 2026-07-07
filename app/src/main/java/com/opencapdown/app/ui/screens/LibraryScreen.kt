package com.opencapdown.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencapdown.app.ui.components.MangaCard
import com.opencapdown.core.OpenCapDownCore
import com.opencapdown.core.domain.models.LibraryManga

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    core: OpenCapDownCore,
    onMangaClick: (sourceId: String, mangaUrl: String) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    var library by remember { mutableStateOf<List<LibraryManga>?>(null) }

    LaunchedEffect(Unit) {
        library = core.getLibrary()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minha Biblioteca", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Search,
                            contentDescription = "Pesquisar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        val currentLibrary = library
        if (currentLibrary == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (currentLibrary.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sua biblioteca está vazia",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Busque e favorite seus mangás para acompanhar a leitura por aqui.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onNavigateToSearch) {
                    Text("Ir para Pesquisa")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(currentLibrary) { manga ->
                    MangaCard(
                        title = manga.title,
                        coverUrl = manga.coverUrl,
                        subtitle = manga.status,
                        onClick = {
                            onMangaClick(manga.sourceId, manga.mangaUrl)
                        }
                    )
                }
            }
        }
    }
}
