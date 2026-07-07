package com.opencapdown.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.opencapdown.app.BuildConfig
import com.opencapdown.app.ui.screens.*
import com.opencapdown.app.ui.theme.OpenCapDownTheme
import com.opencapdown.core.OpenCapDownCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder

fun String.encodeUrl(): String = URLEncoder.encode(this, "UTF-8")
fun String.decodeUrl(): String = URLDecoder.decode(this, "UTF-8")

data class UpdateInfo(val version: String, val downloadUrl: String)

data class GithubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("assets") val assets: List<GithubAsset>
)
data class GithubAsset(
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("name") val name: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenCapDownMainScreen(
    core: OpenCapDownCore,
    onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val client = remember { OkHttpClient() }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }

    // Função para checar atualizações no GitHub
    val checkUpdates: () -> Unit = {
        if (!isCheckingUpdates) {
            isCheckingUpdates = true
            scope.launch(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://api.github.com/repos/Kyura77/OpenCapDown/releases/latest")
                        .header("User-Agent", "OpenCapDown-App")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Unexpected code $response")
                        val body = response.body?.string() ?: ""
                        val release = Gson().fromJson(body, GithubRelease::class.java)
                        
                        val latestVersion = release.tagName.removePrefix("v").trim()
                        val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()

                        if (latestVersion != currentVersion) {
                            // Procura o asset do APK
                            val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                            if (apkAsset != null) {
                                withContext(Dispatchers.Main) {
                                    updateInfo = UpdateInfo(release.tagName, apkAsset.browserDownloadUrl)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Nenhum APK encontrado na última release.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Você já está na versão mais recente ($currentVersion)!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Falha ao verificar atualizações: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isCheckingUpdates = false
                }
            }
        }
    }

    // Função para baixar e instalar o APK
    val startUpdateDownload: (String) -> Unit = { downloadUrl ->
        isDownloadingUpdate = true
        downloadProgress = 0f
        scope.launch(Dispatchers.IO) {
            try {
                val apkFile = File(context.cacheDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()

                val request = Request.Builder().url(downloadUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Failed to download: $response")
                    val body = response.body ?: throw IOException("ResponseBody is null")
                    val contentLength = body.contentLength()
                    
                    body.byteStream().use { inputStream ->
                        apkFile.outputStream().use { outputStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (contentLength > 0) {
                                    withContext(Dispatchers.Main) {
                                        downloadProgress = totalBytesRead.toFloat() / contentLength
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Instala
                withContext(Dispatchers.Main) {
                    isDownloadingUpdate = false
                    updateInfo = null
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isDownloadingUpdate = false
                    Toast.makeText(context, "Falha ao baixar atualização: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    OpenCapDownTheme {
        Scaffold(
            bottomBar = {
                if (currentRoute != null && !currentRoute.startsWith("reader")) {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Biblioteca") },
                            label = { Text("Biblioteca") },
                            selected = currentRoute == "library",
                            onClick = {
                                if (currentRoute != "library") {
                                    navController.navigate("library") {
                                        popUpTo("library") { saveState = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.List, contentDescription = "Downloads") },
                            label = { Text("Downloads") },
                            selected = currentRoute == "downloads",
                            onClick = {
                                if (currentRoute != "downloads") {
                                    navController.navigate("downloads") {
                                        popUpTo("library") { saveState = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                            label = { Text("Ajustes") },
                            selected = currentRoute == "settings",
                            onClick = {
                                if (currentRoute != "settings") {
                                    navController.navigate("settings") {
                                        popUpTo("library") { saveState = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "library",
                modifier = Modifier.padding(padding)
            ) {
                composable("library") {
                    LibraryScreen(
                        core = core,
                        onMangaClick = { sourceId, mangaUrl ->
                            navController.navigate("manga_detail/$sourceId/${mangaUrl.encodeUrl()}")
                        },
                        onNavigateToSearch = {
                            navController.navigate("search")
                        }
                    )
                }

                composable("search") {
                    SearchScreen(
                        core = core,
                        onBack = { navController.popBackStack() },
                        onMangaClick = { sourceId, mangaUrl ->
                            navController.navigate("manga_detail/$sourceId/${mangaUrl.encodeUrl()}")
                        }
                    )
                }

                composable(
                    route = "manga_detail/{sourceId}/{mangaUrl}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("mangaUrl") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val sourceId = backStackEntry.arguments?.getString("sourceId") ?: ""
                    val mangaUrl = backStackEntry.arguments?.getString("mangaUrl")?.decodeUrl() ?: ""
                    MangaDetailScreen(
                        core = core,
                        sourceId = sourceId,
                        mangaUrl = mangaUrl,
                        onBack = { navController.popBackStack() },
                        onChapterClick = { chapterUrl, chapterTitle ->
                            navController.navigate("reader/$sourceId/${chapterTitle.encodeUrl()}/${chapterUrl.encodeUrl()}/${mangaUrl.encodeUrl()}")
                        }
                    )
                }

                composable(
                    route = "reader/{sourceId}/{chapterTitle}/{chapterUrl}/{mangaUrl}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("chapterTitle") { type = NavType.StringType },
                        navArgument("chapterUrl") { type = NavType.StringType },
                        navArgument("mangaUrl") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val sourceId = backStackEntry.arguments?.getString("sourceId") ?: ""
                    val chapterTitle = backStackEntry.arguments?.getString("chapterTitle")?.decodeUrl() ?: ""
                    val chapterUrl = backStackEntry.arguments?.getString("chapterUrl")?.decodeUrl() ?: ""
                    val mangaUrl = backStackEntry.arguments?.getString("mangaUrl")?.decodeUrl() ?: ""
                    ReaderScreen(
                        core = core,
                        sourceId = sourceId,
                        chapterUrl = chapterUrl,
                        chapterTitle = chapterTitle,
                        mangaUrl = mangaUrl,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("downloads") {
                    DownloadsScreen(core = core)
                }

                composable("settings") {
                    SettingsScreen(
                        core = core,
                        onCheckUpdate = { checkUpdates() }
                    )
                }
            }
        }

        // Dialogs de Atualização
        val info = updateInfo
        if (info != null && !isDownloadingUpdate) {
            AlertDialog(
                onDismissRequest = { updateInfo = null },
                title = { Text("Atualização Disponível") },
                text = { Text("Uma nova versão (${info.version}) está disponível. Deseja atualizar agora?") },
                confirmButton = {
                    Button(onClick = { startUpdateDownload(info.downloadUrl) }) {
                        Text("Atualizar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updateInfo = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (isDownloadingUpdate) {
            AlertDialog(
                onDismissRequest = { /* Não fecha no download */ },
                title = { Text("Baixando Atualização") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Baixando APK da nova versão...", modifier = Modifier.padding(bottom = 8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${(downloadProgress * 100).toInt()}%", fontSize = 12.sp)
                    }
                },
                confirmButton = {}
            )
        }
    }
}

private fun installApk(context: Context, apkFile: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
