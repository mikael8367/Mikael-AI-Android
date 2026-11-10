package com.mikael.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun OnlineSearchScreen(
    enabled: Boolean,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasSession by remember { mutableStateOf(OnlineSearchClient.hasSession(context)) }
    var query by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showLogin by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<WebSearchResult?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pesquisa na web", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Pesquisa real via serviço Mikael AI no Render, OpenRouter e Exa. O chat local continua separado.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )

        if (!enabled) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Serviços online desativados", fontWeight = FontWeight.SemiBold)
                    Text("Ative a opção nas Configurações para liberar a pesquisa. Nenhuma consulta será enviada enquanto estiver desativada.")
                    OutlinedButton(onClick = onOpenSettings) { Text("Abrir configurações") }
                }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hasSession) "Sessão Render conectada" else "Conectar ao Mikael AI no Render", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (hasSession) "A sessão fica criptografada no Android Keystore. A senha não é armazenada."
                        else "Entre com a senha APP_PASSWORD configurada no seu serviço Render. A senha será enviada somente ao seu servidor para autenticação.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasSession) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    OnlineSearchClient.logout(context)
                                    hasSession = false
                                    result = null
                                    error = ""
                                }
                            }) { Text("Desconectar") }
                        } else {
                            Button(onClick = { showLogin = true }) { Text("Conectar") }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasSession && !isSearching,
                label = { Text("O que você quer pesquisar?") },
                placeholder = { Text("Ex.: documentação oficial do Kotlin") },
                minLines = 2,
                maxLines = 4
            )
            Button(
                onClick = { showConsent = true },
                enabled = hasSession && query.trim().length >= 2 && !isSearching,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSearching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Pesquisar na web")
            }
            Text(
                "Cada pesquisa exige confirmação. Só o texto deste campo é enviado; histórico, memória, arquivos e código de outras conversas não são anexados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
        }

        if (isSearching) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Pesquisando e verificando fontes…")
            }
        }
        if (error.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(error, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        result?.let { found ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Resultado", fontWeight = FontWeight.Bold)
                    Text(found.summary)
                    Text("Fontes consultadas • ${found.provider}", style = MaterialTheme.typography.labelSmall)
                    found.sources.forEachIndexed { index, source ->
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                                }.onFailure { error = "Não foi possível abrir o link desta fonte." }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${index + 1}. ${source.title}", maxLines = 2)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text("Autorizar esta pesquisa?") },
            text = {
                Text(
                    "Somente a consulta digitada será enviada ao servidor Mikael AI no Render e encaminhada ao OpenRouter/Exa. Nenhuma conversa anterior, memória, arquivo ou código privado será enviado. A pesquisa pode consumir créditos ou limites da conta OpenRouter."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConsent = false
                    isSearching = true
                    error = ""
                    result = null
                    scope.launch {
                        try {
                            result = OnlineSearchClient.search(context, query)
                        } catch (failure: Exception) {
                            error = failure.message ?: "Não foi possível concluir a pesquisa."
                        } finally {
                            isSearching = false
                        }
                    }
                }) { Text("Autorizar e pesquisar") }
            },
            dismissButton = {
                TextButton(onClick = { showConsent = false }) { Text("Cancelar") }
            }
        )
    }

    if (showLogin) {
        AlertDialog(
            onDismissRequest = { if (!isLoggingIn) showLogin = false },
            title = { Text("Conectar ao serviço Render") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Digite a senha APP_PASSWORD configurada no seu serviço Mikael AI. Ela será enviada somente ao seu servidor HTTPS para criar uma sessão.")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.take(256) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha do serviço") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isLoggingIn
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isLoggingIn = true
                        error = ""
                        scope.launch {
                            try {
                                OnlineSearchClient.login(context, password)
                                hasSession = true
                                password = ""
                                showLogin = false
                            } catch (failure: Exception) {
                                error = failure.message ?: "Falha na autenticação."
                            } finally {
                                isLoggingIn = false
                            }
                        }
                    },
                    enabled = password.isNotBlank() && !isLoggingIn
                ) { Text(if (isLoggingIn) "Conectando…" else "Conectar") }
            },
            dismissButton = {
                TextButton(onClick = { if (!isLoggingIn) showLogin = false }) { Text("Cancelar") }
            }
        )
    }
}
