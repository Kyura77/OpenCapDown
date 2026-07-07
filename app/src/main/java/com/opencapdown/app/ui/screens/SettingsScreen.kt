package com.opencapdown.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencapdown.app.BuildConfig
import com.opencapdown.core.OpenCapDownCore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    core: OpenCapDownCore,
    onCheckUpdate: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    var verdinhaToken by remember { mutableStateOf("") }
    var verdinhaMode by remember { mutableStateOf("cdn") }
    var isLoading by remember { mutableStateOf(true) }
    var botTokenVisible by remember { mutableStateOf(false) }
    var verdinhaSenhaVisible by remember { mutableStateOf(false) }
    var verdinhaTokenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val settings = core.getSettings()
        botToken = settings["telegram_bot_token"] ?: ""
        chatId = settings["telegram_chat_id"] ?: ""
        verdinhaToken = settings["verdinha_token"] ?: ""
        verdinhaMode = settings["verdinha_mode"] ?: "cdn"
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Configuração do Telegram", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Bot Token Telegram") },
                    visualTransformation = if (botTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { botTokenVisible = !botTokenVisible }) {
                            Icon(
                                imageVector = if (botTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (botTokenVisible) "Ocultar" else "Mostrar"
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = { Text("Chat ID / Canal Telegram") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Configuração da Verdinha (VIP)", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Modo:")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { verdinhaMode = "cdn" }
                    ) {
                        RadioButton(selected = verdinhaMode == "cdn", onClick = { verdinhaMode = "cdn" })
                        Text("CDN (Sem VIP)")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { verdinhaMode = "vip" }
                    ) {
                        RadioButton(selected = verdinhaMode == "vip", onClick = { verdinhaMode = "vip" })
                        Text("VIP (API)")
                    }
                }

                var verdinhaLogin by remember { mutableStateOf("") }
                var verdinhaSenha by remember { mutableStateOf("") }
                var isLoggingIn by remember { mutableStateOf(false) }
                var loginMessage by remember { mutableStateOf<String?>(null) }

                OutlinedTextField(
                    value = verdinhaLogin,
                    onValueChange = { verdinhaLogin = it },
                    label = { Text("E-mail ou Usuário Verdinha") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = verdinhaSenha,
                    onValueChange = { verdinhaSenha = it },
                    label = { Text("Senha Verdinha") },
                    visualTransformation = if (verdinhaSenhaVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { verdinhaSenhaVisible = !verdinhaSenhaVisible }) {
                            Icon(
                                imageVector = if (verdinhaSenhaVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (verdinhaSenhaVisible) "Ocultar" else "Mostrar"
                            )
                        }
                    }
                )

                if (loginMessage != null) {
                    Text(
                        text = loginMessage!!,
                        color = if (loginMessage!!.startsWith("Sucesso")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            isLoggingIn = true
                            loginMessage = "Autenticando..."
                            val result = core.loginVerdinha(verdinhaLogin, verdinhaSenha)
                            if (result.isSuccess) {
                                loginMessage = "Sucesso: Conectado como VIP!"
                                val settings = core.getSettings()
                                verdinhaToken = settings["verdinha_token"] ?: ""
                                verdinhaMode = "vip" // Muda automaticamente para o modo VIP
                            } else {
                                loginMessage = "Erro: ${result.exceptionOrNull()?.message}"
                            }
                            isLoggingIn = false
                        }
                    },
                    enabled = !isLoggingIn && verdinhaLogin.isNotBlank() && verdinhaSenha.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Autenticar na Verdinha")
                    }
                }

                OutlinedTextField(
                    value = verdinhaToken,
                    onValueChange = { verdinhaToken = it },
                    label = { Text("Token VIP Verdinha (Gerado)") },
                    visualTransformation = if (verdinhaTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { verdinhaTokenVisible = !verdinhaTokenVisible }) {
                            Icon(
                                imageVector = if (verdinhaTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (verdinhaTokenVisible) "Ocultar" else "Mostrar"
                            )
                        }
                    }
                )

                Button(
                    onClick = {
                        scope.launch {
                            core.updateTelegramConfig(botToken, chatId)
                            core.updateVerdinhaConfig(verdinhaToken)
                            core.updateVerdinhaMode(verdinhaMode)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salvar Todas as Configurações")
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text("Aplicativo", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)

                Button(
                    onClick = onCheckUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Procurar Atualizações")
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = "Versão ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
