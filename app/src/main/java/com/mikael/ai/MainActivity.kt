package com.mikael.ai

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AssistChip
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val messageIds = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() * 1000L)
private fun nextMessageId(): Long = messageIds.incrementAndGet()

private enum class AppTab(val label: String, val symbol: String) {
    CHAT("Chat", "◌"),
    MODELS("Modelos", "▤"),
    SETTINGS("Configurações", "⚙")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = AppPreferences(this)
        setContent { MikaelTheme { MikaelOfflineApp(preferences) } }
    }

    override fun onDestroy() {
        if (isFinishing) {
            LocalInferenceEngine.cancel()
            Thread { LocalInferenceEngine.unload() }.start()
        }
        super.onDestroy()
    }
}

@Composable
private fun MikaelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF39D98A),
            onPrimary = Color(0xFF062216),
            secondary = Color(0xFF8BE7B7),
            background = Color(0xFF0B100E),
            surface = Color(0xFF151D19),
            surfaceVariant = Color(0xFF202B25),
            onBackground = Color(0xFFF1F5F2),
            onSurface = Color(0xFFF1F5F2)
        ),
        content = content
    )
}

@Composable
private fun MikaelOfflineApp(preferences: AppPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activityManager = remember(context) {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(ConversationStore.load(context)) }
    }
    var tab by remember { mutableStateOf(AppTab.CHAT) }
    var input by remember { mutableStateOf("") }
    var modelPath by remember { mutableStateOf(preferences.modelPath) }
    var models by remember { mutableStateOf(installedModels(context)) }
    var memoryLimit by remember { mutableStateOf(preferences.memoryLimitMiB) }
    var contextTokens by remember { mutableStateOf(preferences.contextTokens) }
    var reloadKey by remember { mutableStateOf(0) }
    var engineReady by remember { mutableStateOf(false) }
    var engineStatus by remember { mutableStateOf("Importe ou baixe um modelo GGUF para começar.") }
    var generating by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var mode by remember { mutableStateOf(AssistantMode.CODING) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatus by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf<List<ChatAttachment>>(emptyList()) }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            try {
                val added = withContext(Dispatchers.IO) { importChatAttachments(context, uris) }
                attachments = (attachments + added).distinctBy { it.path }.take(5)
            } catch (error: Exception) {
                engineStatus = error.message ?: "Falha ao anexar arquivos."
            }
        }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    val imported = withContext(Dispatchers.IO) { importModel(context, uri) }
                    engineReady = false
                    LocalInferenceEngine.cancel()
                    withContext(Dispatchers.IO) { LocalInferenceEngine.unload() }
                    modelPath = imported.absolutePath
                    preferences.modelPath = modelPath
                    models = installedModels(context)
                    engineStatus = "Modelo importado. Preparando o motor local..."
                    tab = AppTab.MODELS
                    reloadKey++
                } catch (error: Exception) {
                    engineStatus = error.message ?: "Não foi possível importar o modelo."
                } finally {
                    busy = false
                }
            }
        }
    }

    LaunchedEffect(modelPath, memoryLimit, contextTokens, reloadKey) {
        engineReady = false
        LocalInferenceEngine.cancel()
        withContext(Dispatchers.IO) { LocalInferenceEngine.unload() }
        val path = modelPath
        if (path.isNullOrBlank()) {
            engineStatus = "Nenhum modelo selecionado. Importe um GGUF ou baixe o modelo recomendado."
            return@LaunchedEffect
        }
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) {
            engineStatus = "O arquivo selecionado não existe mais. Importe ou selecione outro modelo."
            return@LaunchedEffect
        }
        val check = MemoryBudget.check(
            availableBytes = MemoryBudget.availableBytes(activityManager),
            selectedLimitMiB = memoryLimit,
            modelBytes = file.length(),
            contextTokens = contextTokens
        )
        if (!check.allowed) {
            engineStatus = check.reason ?: "Memória insuficiente para carregar o modelo."
            return@LaunchedEffect
        }
        engineStatus = "Carregando modelo GGUF no aparelho..."
        val result = withContext(Dispatchers.IO) {
            LocalInferenceEngine.load(file.absolutePath, contextTokens)
        }
        result.fold(
            onSuccess = {
                engineReady = true
                engineStatus = "Modelo pronto • inferência 100% local"
            },
            onFailure = {
                engineReady = false
                engineStatus = it.message ?: "Falha ao carregar o modelo."
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF09150F)) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.symbol, fontWeight = FontWeight.Bold) },
                        label = { Text(item.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).background(Color(0xFF173D2B), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("M", color = Color(0xFF54E69C), fontWeight = FontWeight.Black) }
                Column(Modifier.padding(start = 11.dp)) {
                    Text("Mikael AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("IA LOCAL • PRIVACIDADE NO DISPOSITIVO", color = Color(0xFF8BE7B7), style = MaterialTheme.typography.labelSmall)
                    Text("v1.6.0", color = Color(0xFF718779), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (engineReady) "● LOCAL" else "○ LOCAL",
                    color = if (engineReady) Color(0xFF54E69C) else Color(0xFFB8C6BD),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 12 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 12 })
                },
                label = "offline-tab-transition"
            ) { selected ->
                when (selected) {
                    AppTab.CHAT -> OfflineChatScreen(
                        messages = messages,
                        input = input,
                        onInputChange = { input = it },
                        attachments = attachments,
                        onAttach = { attachmentPicker.launch(arrayOf("*/*")) },
                        onRemoveAttachment = { item ->
                            attachments = attachments - item
                            File(item.path).delete()
                        },
                        mode = mode,
                        onModeChange = { mode = it },
                        status = engineStatus,
                        ready = engineReady && !busy && !downloading,
                        generating = generating,
                        onCancel = {
                            generationJob?.cancel()
                            LocalInferenceEngine.cancel()
                        },
                        onOpenModels = { tab = AppTab.MODELS },
                        onNewChat = {
                            messages.clear()
                            attachments.forEach { File(it.path).delete() }
                            attachments = emptyList()
                            ConversationStore.clear(context)
                        },
                        onSend = {
                            val prompt = input.trim().ifBlank { if (attachments.isNotEmpty()) "Analise os anexos selecionados." else "" }
                            if (prompt.isNotEmpty() && engineReady && !generating) {
                                input = ""
                                val attachmentContext = attachments.joinToString("\n\n") { it.promptContext }
                                attachments.forEach { File(it.path).delete() }
                                val completePrompt = buildString {
                                    append(prompt)
                                    if (attachmentContext.isNotBlank()) append("\n\nAnexos selecionados:\n").append(attachmentContext)
                                }
                                messages.add(ChatMessage(nextMessageId(), "user", completePrompt))
                                attachments = emptyList()
                                ConversationStore.save(context, messages)
                                generating = true
                                val selectedMode = mode
                                generationJob = scope.launch {
                                    try {
                                        val conversationPrompt = AssistantPromptBuilder.build(
                                            mode = selectedMode,
                                            history = messages.takeLast(6).map { it.role to it.text },
                                            contextTokens = contextTokens
                                        )
                                        val response = withContext(Dispatchers.IO) {
                                            LocalInferenceEngine.generate(
                                                conversationPrompt,
                                                AssistantPromptBuilder.maxTokens(selectedMode, contextTokens)
                                            ).getOrThrow()
                                        }
                                        if (response.isNotBlank()) {
                                            messages.add(ChatMessage(nextMessageId(), "assistant", response.trim()))
                                            ConversationStore.save(context, messages)
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        messages.add(ChatMessage(
                                            nextMessageId(), "assistant",
                                            "Erro no motor local: ${error.message ?: "não foi possível gerar a resposta."}"
                                        ))
                                        ConversationStore.save(context, messages)
                                    } finally {
                                        generating = false
                                        generationJob = null
                                    }
                                }
                            }
                        }
                    )
                    AppTab.MODELS -> OfflineModelsScreen(
                        models = models,
                        selectedPath = modelPath,
                        status = engineStatus,
                        busy = busy || downloading || generating,
                        downloadProgress = downloadProgress,
                        downloadStatus = downloadStatus,
                        onImport = { modelPicker.launch(arrayOf("*/*")) },
                        onSelect = { path ->
                            if (!generating && !busy) {
                                engineReady = false
                                modelPath = path
                                preferences.modelPath = path
                                reloadKey++
                            }
                        },
                        onUnload = {
                            if (!generating) {
                                engineReady = false
                                scope.launch {
                                LocalInferenceEngine.cancel()
                                withContext(Dispatchers.IO) { LocalInferenceEngine.unload() }
                                engineReady = false
                                engineStatus = "Modelo descarregado da memória."
                                }
                            }
                        },
                        onDelete = { path ->
                            if (!generating && !busy) {
                                if (path == modelPath) engineReady = false
                                scope.launch {
                                if (path == modelPath) {
                                    LocalInferenceEngine.cancel()
                                    withContext(Dispatchers.IO) { LocalInferenceEngine.unload() }
                                    engineReady = false
                                    modelPath = null
                                    preferences.modelPath = null
                                }
                                val target = File(path)
                                if (target.exists() && !target.delete()) {
                                    engineStatus = "Não foi possível excluir o arquivo do modelo."
                                } else {
                                    models = installedModels(context)
                                    engineStatus = "Modelo excluído do armazenamento do aplicativo."
                                }
                                }
                            }
                        },
                        onDownload = {
                            if (!downloading && !generating) {
                                downloading = true
                                downloadProgress = 0f
                                downloadStatus = "Preparando download do modelo recomendado..."
                                scope.launch {
                                    try {
                                        val downloaded = withContext(Dispatchers.IO) {
                                            RecommendedModelDownloader.download(File(context.filesDir, "models")) { current, total ->
                                                scope.launch {
                                                    downloadProgress = if (total > 0L) current.toFloat() / total else 0f
                                                    downloadStatus = "Baixando modelo: ${(downloadProgress * 100).toInt()}%"
                                                }
                                            }
                                        }
                                        engineReady = false
                                        LocalInferenceEngine.cancel()
                                        withContext(Dispatchers.IO) { LocalInferenceEngine.unload() }
                                        modelPath = downloaded.absolutePath
                                        preferences.modelPath = modelPath
                                        models = installedModels(context)
                                        if (memoryLimit < 1024) {
                                            memoryLimit = 1024
                                            preferences.memoryLimitMiB = memoryLimit
                                        }
                                        reloadKey++
                                        downloadStatus = "Download concluído e SHA-256 validado."
                                    } catch (error: Exception) {
                                        downloadStatus = error.message ?: "Falha no download."
                                    } finally {
                                        downloading = false
                                    }
                                }
                            }
                        }
                    )
                    AppTab.SETTINGS -> OfflineSettingsScreen(
                        memoryLimit = memoryLimit,
                        contextTokens = contextTokens,
                        availableBytes = MemoryBudget.availableBytes(activityManager),
                        onMemoryChange = {
                            engineReady = false
                            memoryLimit = it
                            preferences.memoryLimitMiB = it
                        },
                        onContextChange = {
                            engineReady = false
                            contextTokens = it
                            preferences.contextTokens = it
                        },
                        onClearHistory = {
                            messages.clear()
                            ConversationStore.clear(context)
                        },
                        onReload = { if (!generating) { engineReady = false; reloadKey++ } },
                        reloadEnabled = !generating,
                        controlsEnabled = !generating,
                        onGitHubContextLoaded = { repoContext ->
                            input = repoContext
                            tab = AppTab.CHAT
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineChatScreen(
    messages: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    attachments: List<ChatAttachment>,
    onAttach: () -> Unit,
    onRemoveAttachment: (ChatAttachment) -> Unit,
    mode: AssistantMode,
    onModeChange: (AssistantMode) -> Unit,
    status: String,
    ready: Boolean,
    generating: Boolean,
    onCancel: () -> Unit,
    onOpenModels: () -> Unit,
    onNewChat: () -> Unit,
    onSend: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (ready) "Modelo carregado" else "Modelo não carregado", fontWeight = FontWeight.SemiBold)
                Text(status, color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
                if (!ready && (status.contains("Nenhum modelo") || status.contains("Importe ou baixe"))) {
                    Text(
                        "Nenhum modelo GGUF está instalado. O APK não inclui os pesos do modelo.",
                        color = Color(0xFFFFD18A),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = onOpenModels) { Text("Instalar modelo") }
                }
            }
            OutlinedButton(onClick = onNewChat, enabled = !generating) { Text("Nova conversa") }
        }
        if (messages.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Mikael AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Seu assistente de programação executado diretamente no celular.",
                    modifier = Modifier.padding(top = 10.dp),
                    color = Color(0xFF9EAAA2),
                    textAlign = TextAlign.Center
                )
                Text(
                    "Depois de importar o modelo, você pode conversar sem internet.",
                    modifier = Modifier.padding(top = 10.dp),
                    color = Color(0xFF8BE7B7),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message -> OfflineMessageBubble(message) }
                if (generating) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("  Mikael AI está processando localmente...", color = Color(0xFF9EAAA2))
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFF26332B))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistantMode.entries.forEach { item ->
                FilterChip(
                    selected = mode == item,
                    onClick = { onModeChange(item) },
                    enabled = !generating,
                    label = { Text(item.label, maxLines = 1) }
                )
            }
        }
        if (attachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachments.forEach { item ->
                    AssistChip(onClick = { onRemoveAttachment(item) }, label = { Text("✕ ${item.name}", maxLines = 1) })
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedButton(onClick = onAttach, enabled = !generating && attachments.size < 5, modifier = Modifier.height(54.dp)) { Text("＋") }
            Spacer(Modifier.size(6.dp))
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mensagem ou anexos...") },
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
                enabled = !generating,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (ready) onSend() })
            )
            Spacer(Modifier.size(8.dp))
            if (generating) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.height(54.dp)) { Text("Parar") }
            } else {
                Button(
                    onClick = onSend,
                    enabled = ready && (input.isNotBlank() || attachments.isNotEmpty()),
                    modifier = Modifier.height(54.dp)
                ) { Text("Enviar") }
            }
        }
    }
}

@Composable
private fun OfflineMessageBubble(message: ChatMessage) {
    val fromUser = message.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (fromUser) Color(0xFF1B5B3A) else Color(0xFF1B2420),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Text(message.text, Modifier.padding(14.dp), color = Color(0xFFF1F5F2))
        }
    }
}

@Composable
private fun OfflineModelsScreen(
    models: List<File>,
    selectedPath: String?,
    status: String,
    busy: Boolean,
    downloadProgress: Float,
    downloadStatus: String,
    onImport: () -> Unit,
    onSelect: (String) -> Unit,
    onUnload: () -> Unit,
    onDelete: (String) -> Unit,
    onDownload: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Modelos locais", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Os arquivos GGUF ficam no armazenamento privado do aplicativo. A geração nunca envia mensagens para servidores.", color = Color(0xFF9EAAA2))
        Button(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Importar modelo GGUF")
        }
        OutlinedButton(onClick = onDownload, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Baixar modelo recomendado • Qwen 0.5B Q4")
        }
        if (busy && downloadStatus.isNotBlank()) {
            if (downloadProgress > 0f) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { downloadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else CircularProgressIndicator(Modifier.size(22.dp))
            Text(downloadStatus, color = Color(0xFF8BE7B7))
        }
        Text(status, color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(color = Color(0xFF26332B))
        Text("Instalados: ${models.size}", fontWeight = FontWeight.SemiBold)
        if (models.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF202B25))) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nenhum modelo instalado", fontWeight = FontWeight.Bold, color = Color(0xFFFFD18A))
                    Text(
                        "Baixe o Qwen 0.5B recomendado (aproximadamente 469 MiB) ou importe um arquivo GGUF. Depois que o modelo estiver instalado, a conversa poderá funcionar offline.",
                        color = Color(0xFFCFD8D1),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(models, key = { it.absolutePath }) { file ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D19))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(file.name, fontWeight = FontWeight.SemiBold)
                            Text("${file.length() / (1024L * 1024L)} MiB", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (file.absolutePath == selectedPath) {
                                    Text("Selecionado", color = Color(0xFF54E69C), modifier = Modifier.padding(top = 10.dp))
                                } else {
                                    OutlinedButton(onClick = { onSelect(file.absolutePath) }, enabled = !busy) { Text("Usar") }
                                }
                                OutlinedButton(onClick = onUnload, enabled = !busy && file.absolutePath == selectedPath) { Text("Descarregar") }
                                OutlinedButton(onClick = { onDelete(file.absolutePath) }, enabled = !busy) { Text("Excluir") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineSettingsScreen(
    memoryLimit: Int,
    contextTokens: Int,
    availableBytes: Long,
    onMemoryChange: (Int) -> Unit,
    onContextChange: (Int) -> Unit,
    onClearHistory: () -> Unit,
    onReload: () -> Unit,
    reloadEnabled: Boolean,
    controlsEnabled: Boolean,
    onGitHubContextLoaded: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Configurações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("A geração acontece no aparelho. A internet só é usada quando você escolhe baixar um modelo ou conectar o GitHub.", color = Color(0xFF9EAAA2))
        Text("Orçamento de memória: $memoryLimit MiB", fontWeight = FontWeight.SemiBold)
        Text("RAM livre informada pelo Android: ${MemoryBudget.mib(availableBytes)} MiB", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MemoryBudget.allowedLimitsMiB.forEach { limit ->
                FilterChip(
                    selected = memoryLimit == limit,
                    onClick = { onMemoryChange(limit) },
                    enabled = controlsEnabled,
                    label = { Text(if (limit >= 1024) "${limit / 1024.0} GB" else "$limit MB") }
                )
            }
        }
        Text("Contexto: $contextTokens tokens", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(512, 1024, 2048, 4096).forEach { count ->
                FilterChip(
                    selected = contextTokens == count,
                    onClick = { onContextChange(count) },
                    enabled = controlsEnabled,
                    label = { Text(count.toString()) }
                )
            }
        }
        Text(
            "A estimativa de RAM é conservadora, mas não representa um limite absoluto do Android. Modelos maiores podem ficar lentos ou não caber no aparelho.",
            color = Color(0xFF9EAAA2),
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(onClick = onReload, enabled = reloadEnabled, modifier = Modifier.fillMaxWidth()) { Text("Recarregar modelo") }
        OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) { Text("Apagar histórico local") }
        GitHubConnectorCard(onContextLoaded = onGitHubContextLoaded)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151D19))) {
            Column(Modifier.padding(14.dp)) {
                Text("Privacidade", fontWeight = FontWeight.Bold)
                Text("As conversas e a inferência ficam no aparelho. O conector GitHub só acessa a conta quando você autoriza.", color = Color(0xFF9EAAA2))
            }
        }
    }
}

private fun installedModels(context: Context): List<File> =
    File(context.filesDir, "models").listFiles()
        ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

private fun importModel(context: Context, uri: Uri): File {
    val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
    } ?: "modelo.gguf"
    require(displayName.endsWith(".gguf", ignoreCase = true)) {
        "Selecione um arquivo com extensão .gguf."
    }
    val directory = File(context.filesDir, "models")
    check(directory.exists() || directory.mkdirs()) { "Não foi possível criar a pasta de modelos." }
    val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val uniqueName = "${System.currentTimeMillis()}_$safeName"
    val destination = File(directory, uniqueName)
    val temporary = File(directory, "$uniqueName.importing")
    try {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "O Android não conseguiu abrir o arquivo selecionado." }
            temporary.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
        }
        require(temporary.length() > 1024L * 1024L) { "O arquivo parece pequeno demais para um modelo GGUF válido." }
        val validHeader = temporary.inputStream().use { stream ->
            val header = ByteArray(4)
            stream.read(header) == 4 && header.decodeToString() == "GGUF"
        }
        require(validHeader) { "O arquivo selecionado não tem o cabeçalho GGUF esperado." }
        check(temporary.renameTo(destination)) { "Não foi possível concluir a importação do modelo." }
        return destination
    } catch (error: Exception) {
        temporary.delete()
        throw error
    }
}
