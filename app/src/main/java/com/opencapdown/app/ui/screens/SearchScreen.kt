package com.opencapdown.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencapdown.app.ui.components.MangaCard
import com.opencapdown.app.ui.components.ShimmerGrid
import com.opencapdown.core.OpenCapDownCore
import com.opencapdown.core.domain.models.SearchResult
import com.opencapdown.core.domain.models.SourceInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    core: OpenCapDownCore,
    onBack: () -> Unit,
    onMangaClick: (sourceId: String, mangaUrl: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var query by remember { mutableStateOf("") }
    var sources by remember { mutableStateOf<List<SourceInfo>>(emptyList()) }
    var selectedSourceId by remember { mutableStateOf<String?>(null) } // null means "Todas as fontes"
    
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResult>?>(null) }

    var isDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sources = core.getSources()
    }

    val performSearch: () -> Unit = {
        if (query.trim().isNotEmpty()) {
            keyboardController?.hide()
            isSearching = true
            scope.launch {
                val sourceId = selectedSourceId
                val results = if (sourceId != null) {
                    core.search(sourceId, query.trim())
                } else {
                    core.search(query.trim())
                }
                searchResults = results
                isSearching = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pesquisa", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Linha de Busca e Seleção de Fontes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar mangás...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                    modifier = Modifier.weight(1f)
                )

                Box {
                    Button(onClick = { isDropdownExpanded = true }) {
                        val currentSource = sources.find { it.id == selectedSourceId }
                        Text(currentSource?.name ?: "Todas")
                    }
                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Todas as fontes") },
                            onClick = {
                                selectedSourceId = null
                                isDropdownExpanded = false
                                performSearch()
                            }
                        )
                        sources.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source.name) },
                                onClick = {
                                    selectedSourceId = source.id
                                    isDropdownExpanded = false
                                    performSearch()
                                }
                            )
                        }
                    }
                }
            }

            // Resultados
            Box(modifier = Modifier.weight(1f)) {
                val currentResults = searchResults
                if (isSearching) {
                    ShimmerGrid()
                } else if (currentResults == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Digite um termo e pressione Pesquisar.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else if (currentResults.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Nenhum resultado encontrado",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tente usar outras palavras-chave ou selecione outra fonte.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(currentResults) { manga ->
                            MangaCard(
                                title = manga.title,
                                coverUrl = manga.coverUrl,
                                subtitle = sources.find { it.id == manga.sourceId }?.name ?: manga.sourceId,
                                onClick = { onMangaClick(manga.sourceId, manga.url) }
                            )
                        }
                    }
                }
            }
        }
    }
}
