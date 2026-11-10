package com.mikael.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.app.ActivityManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class AppTab(val title: String, val symbol: String) {
    CHAT("Chat", "◉"),
    MODELS("Modelos", "▤"),
    GITHUB("GitHub", "◈"),
    SETTINGS("Configurações", "⚙")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = AppPreferences(this)
        setContent {
            MikaelAiTheme {
                MikaelAiApp(preferences)
            }
        }
    }
}

@Composable
private fun MikaelAiTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Color(0xFF39D98A),
        onPrimary = Color(0xFF062216),
        secondary = Color(0xFF8BE7B7),
        background = Color(0xFF0B100E),
        surface = Color(0xFF151D19),
        surfaceVariant = Color(0xFF202B25),
        onBackground = Color(0xFFF1F5F2),
        onSurface = Color(0xFFF1F5F2)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun MikaelAiApp(preferences: AppPreferences) {
    val context = LocalContext.current
    val activityManager = remember(context) {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    val scope = rememberCoroutineScope()
    val conversations = remember {
        mutableStateListOf<ChatConversation>().apply { addAll(ConversationStore.loadAll(context)) }
    }
    var activeConversationId by remember { mutableStateOf(conversations.firstOrNull()?.id ?: java.util.UUID.randomUUID().toString()) }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(conversations.firstOrNull()?.messages.orEmpty()) }
    }
    var showConversationList by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(AppTab.CHAT) }

    var modelPath by remember { mutableStateOf(preferences.modelPath) }
    var installedModels by remember { mutableStateOf(listInstalledModels(context)) }
    var catalogInstalled by remember { mutableStateOf(loadCatalogInstalled(context)) }
    var catalogProgress by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var catalogErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedCatalogModelId by remember { mutableStateOf(preferences.selectedCatalogModelId) }
    var llamaReady by remember { mutableStateOf(false) }
    var llamaStatus by remember { mutableStateOf("Instale um modelo na aba Modelos para começar.") }
    var memoryLimit by remember { mutableStateOf(preferences.memoryLimitMiB) }
    var contextTokens by remember { mutableStateOf(preferences.contextTokens) }
    var modelReloadCounter by remember { mutableStateOf(0) }
    var engineReady by remember { mutableStateOf(false) }
    var engineStatus by remember { mutableStateOf("Importe um modelo autoral .mkai para começar.") }
    var isGenerating by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var githubClientId by remember { mutableStateOf(preferences.githubClientId) }
    var allowOnlineFeatures by remember { mutableStateOf(preferences.allowOnlineFeatures) }
    var userMemoryEnabled by remember { mutableStateOf(UserMemoryStore.isEnabled(context)) }
    var userMemoryItems by remember { mutableStateOf(UserMemoryStore.load(context)) }
    var pendingTask by remember { mutableStateOf(PendingTaskStore.load(context)) }
    var userMemoryCount by remember { mutableStateOf(userMemoryItems.size) }
    var githubContext by remember { mutableStateOf("") }

    fun persistActiveConversation() {
        if (messages.isEmpty()) return
        val index = conversations.indexOfFirst { it.id == activeConversationId }
        val firstPrompt = messages.firstOrNull { it.role == "user" }?.text.orEmpty()
        val generatedTitle = firstPrompt.replace(Regex("\\s+"), " ").trim().let {
            if (it.length > 42) it.take(42).trimEnd() + "…" else it
        }.ifBlank { "Nova conversa" }
        val title = conversations.getOrNull(index)?.title ?: generatedTitle
        val updated = ChatConversation(activeConversationId, title, System.currentTimeMillis(), messages.toList())
        if (index >= 0) conversations[index] = updated else conversations.add(updated)
        val ordered = conversations.sortedByDescending { it.updatedAt }
        conversations.clear()
        conversations.addAll(ordered)
        ConversationStore.saveAll(context, conversations)
    }

    fun startNewConversation() {
        if (isGenerating) return
        messages.clear()
        activeConversationId = java.util.UUID.randomUUID().toString()
        input = ""
        showConversationList = false
        tab = AppTab.CHAT
    }

    fun openConversation(conversation: ChatConversation) {
        if (isGenerating) return
        messages.clear()
        messages.addAll(conversation.messages)
        activeConversationId = conversation.id
        showConversationList = false
        input = ""
        tab = AppTab.CHAT
    }

    LaunchedEffect(Unit) {
        if (modelPath.isNullOrBlank() && catalogInstalled.isEmpty()) {
            tab = AppTab.MODELS
        }
    }

    LaunchedEffect(pendingTask?.completedResponse) {
        val task = pendingTask
        val response = task?.completedResponse ?: return@LaunchedEffect
        val existing = conversations.firstOrNull { it.id == task.conversationId }
        val restoredMessages = existing?.messages?.toMutableList() ?: mutableListOf()
        if (restoredMessages.none { it.role == "user" && it.text == task.prompt }) {
            restoredMessages.add(ChatMessage(task.createdAt, "user", task.prompt))
        }
        if (restoredMessages.none { it.role == "assistant" && it.text == response }) {
            restoredMessages.add(ChatMessage(System.currentTimeMillis(), "assistant", response))
        }
        val title = existing?.title ?: task.prompt.replace(Regex("\\s+"), " ").take(42)
        val restored = ChatConversation(task.conversationId, title, System.currentTimeMillis(), restoredMessages)
        val index = conversations.indexOfFirst { it.id == task.conversationId }
        if (index >= 0) conversations[index] = restored else conversations.add(restored)
        ConversationStore.saveAll(context, conversations)
        if (activeConversationId == task.conversationId) {
            messages.clear()
            messages.addAll(restoredMessages)
        }
        PendingTaskStore.clear(context)
        pendingTask = null
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val copiedPath = withContext(Dispatchers.IO) { copyModelToPrivateStorage(context, uri) }
                    LocalInferenceEngine.cancel()
                    LlamaInferenceEngine.cancel()
                    withContext(Dispatchers.IO) {
                        LocalInferenceEngine.unload()
                        LlamaInferenceEngine.unload()
                    }
                    preferences.modelPath = copiedPath
                    modelPath = copiedPath
                    selectedCatalogModelId = "mikael-base"
                    preferences.selectedCatalogModelId = "mikael-base"
                    installedModels = listInstalledModels(context)
                    tab = AppTab.MODELS
                    engineStatus = "Modelo importado. Verificando memória..."
                } catch (error: Exception) {
                    engineStatus = error.message ?: "Não foi possível importar o modelo."
                }
            }
        }
    }

    LaunchedEffect(modelPath, memoryLimit, contextTokens, modelReloadCounter, selectedCatalogModelId) {
        engineReady = false
        if (selectedCatalogModelId != "mikael-base") {
            LocalInferenceEngine.cancel()
            withContext(Dispatchers.IO) { LocalInferenceEngine.unload() }
            return@LaunchedEffect
        }
        if (modelPath.isNullOrBlank()) {
            engineStatus = "Importe um modelo autoral .mkai para começar."
            return@LaunchedEffect
        }
        val file = File(modelPath!!)
        if (!file.isFile || file.length() <= 0L) {
            engineStatus = "O arquivo do modelo não foi encontrado."
            return@LaunchedEffect
        }
        val integrityValid = MikaelModelInstaller.verifyInstalledFile(file)
        if (!integrityValid) {
            engineStatus = "O checkpoint MKAI está corrompido ou foi alterado. Reinstale o modelo."
            return@LaunchedEffect
        }
        LocalInferenceEngine.cancel()
        withContext(Dispatchers.IO) { LocalInferenceEngine.unload() }
        val effectiveMkaiContext = minOf(
            contextTokens,
            MikaelModelInstaller.maxSequenceTokens(file) ?: contextTokens
        )
        val check = MemoryBudget.check(
            availableBytes = MemoryBudget.availableBytes(activityManager),
            selectedLimitMiB = memoryLimit,
            modelBytes = file.length(),
            contextTokens = effectiveMkaiContext
        )
        if (!check.allowed) {
            engineStatus = check.reason ?: "Memória insuficiente."
            return@LaunchedEffect
        }
        engineStatus = "Carregando modelo..."
        val loadResult = withContext(Dispatchers.IO) {
            LocalInferenceEngine.load(file.absolutePath, effectiveMkaiContext)
        }
        loadResult.fold(
            onSuccess = {
                engineReady = true
                engineStatus = "Modelo pronto • orçamento de $memoryLimit MiB"
            },
            onFailure = {
                engineReady = false
                engineStatus = it.message ?: "Falha ao carregar o modelo."
            }
        )
    }

    LaunchedEffect(selectedCatalogModelId, catalogInstalled[selectedCatalogModelId], contextTokens) {
        llamaReady = false
        if (selectedCatalogModelId == "mikael-base") {
            LlamaInferenceEngine.cancel()
            withContext(Dispatchers.IO) { LlamaInferenceEngine.unload() }
            return@LaunchedEffect
        }
        val file = catalogInstalled[selectedCatalogModelId]
        if (file == null || !file.isFile || file.length() < 1024L) {
            llamaStatus = "Instale este modelo no catálogo antes de selecioná-lo."
            return@LaunchedEffect
        }
        val checksumValid = ModelInstaller.verifyInstalledFile(file)
        if (!checksumValid) {
            llamaStatus = "Checksum SHA-256 inválido ou ausente. Reinstale este modelo pelo catálogo."
            return@LaunchedEffect
        }
        LlamaInferenceEngine.cancel()
        withContext(Dispatchers.IO) { LlamaInferenceEngine.unload() }
        LocalInferenceEngine.cancel()
        withContext(Dispatchers.IO) { LocalInferenceEngine.unload() }
        val memoryCheck = MemoryBudget.check(
            availableBytes = MemoryBudget.availableBytes(activityManager),
            selectedLimitMiB = memoryLimit,
            modelBytes = file.length(),
            contextTokens = contextTokens.coerceIn(128, 2048)
        )
        if (!memoryCheck.allowed) {
            llamaStatus = memoryCheck.reason ?: "Memória insuficiente para carregar este GGUF."
            return@LaunchedEffect
        }
        llamaStatus = "Carregando GGUF no llama.cpp..."
        val result = withContext(Dispatchers.IO) {
            LlamaInferenceEngine.load(file.absolutePath, contextTokens.coerceIn(128, 2048))
        }
        result.fold(
            onSuccess = {
                llamaReady = true
                llamaStatus = "Modelo GGUF pronto • contexto ${LlamaInferenceEngine.contextCapacityTokens()}"
            },
            onFailure = {
                llamaReady = false
                llamaStatus = it.message ?: "Falha ao carregar o modelo GGUF."
            }
        )
    }

    val activeEngineReady = if (selectedCatalogModelId == "mikael-base") engineReady else llamaReady
    val activeEngineStatus = if (selectedCatalogModelId == "mikael-base") engineStatus else llamaStatus

    fun launchGeneration(prompt: String) {
        if (ThinkingService.hasActiveTask()) {
            isGenerating = true
            return
        }
        val modelId = selectedCatalogModelId
        val task = PendingAiTask(activeConversationId, prompt, modelId, System.currentTimeMillis(), githubContext)
        PendingTaskStore.save(context, task)
        pendingTask = task
        isGenerating = true
        ContextCompat.startForegroundService(context, Intent(context, ThinkingService::class.java))
        ThinkingService.launchTask {
            try {
                val activeContext = if (modelId == "mikael-base") {
                    LocalInferenceEngine.contextCapacityTokens()
                } else {
                    LlamaInferenceEngine.contextCapacityTokens()
                }
                val conversationPrompt = ConversationPrompt.build(
                    messages = messages.toList(),
                    githubContext = githubContext,
                    contextCapacityBytes = activeContext,
                    userMemory = UserMemoryStore.contextForPrompt(context)
                )
                val result = withContext(Dispatchers.IO) {
                    AnswerRevision.generate(
                        originalRequest = prompt,
                        conversationPrompt = conversationPrompt,
                        contextCapacityBytes = activeContext,
                        maxAnswerTokens = 256
                    ) { candidatePrompt, tokenLimit ->
                        if (modelId == "mikael-base") LocalInferenceEngine.generate(candidatePrompt, tokenLimit)
                        else LlamaInferenceEngine.generate(candidatePrompt, tokenLimit)
                    }
                }
                result.fold(
                    onSuccess = { answer ->
                        if (answer.isNotBlank()) {
                            PendingTaskStore.complete(context, answer)
                            ConversationStore.append(messages, ChatMessage(System.currentTimeMillis(), "assistant", answer))
                            persistActiveConversation()
                            PendingTaskStore.clear(context)
                            pendingTask = null
                        }
                    },
                    onFailure = { error ->
                        if (error.message != "Geração cancelada pelo usuário.") {
                            ConversationStore.append(messages, ChatMessage(System.currentTimeMillis(), "assistant",
                                "Erro local: ${error.message ?: "não foi possível gerar a resposta."}"))
                            persistActiveConversation()
                            // Mantém o registro para permitir tentar novamente após corrigir a falha.
                        }
                    }
                )
            } catch (error: Exception) {
                if (error !is kotlinx.coroutines.CancellationException) {
                    ConversationStore.append(messages, ChatMessage(System.currentTimeMillis(), "assistant",
                        "Tarefa interrompida: ${error.message ?: "erro inesperado"}. Você pode tentar retomá-la."))
                    persistActiveConversation()
                }
            } finally {
                isGenerating = false
                context.stopService(Intent(context, ThinkingService::class.java))
            }
        }
    }

    fun resumePendingTask() {
        val task = pendingTask ?: return
        if (isGenerating || ThinkingService.hasActiveTask()) {
            isGenerating = true
            return
        }
        if (selectedCatalogModelId != task.modelId) {
            selectedCatalogModelId = task.modelId
            preferences.selectedCatalogModelId = task.modelId
            modelReloadCounter++
            android.widget.Toast.makeText(context, "Carregando o modelo usado na tarefa interrompida.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (!activeEngineReady) return
        githubContext = task.githubContext
        val conversation = conversations.firstOrNull { it.id == task.conversationId }
        if (conversation != null) {
            messages.clear()
            messages.addAll(conversation.messages)
            activeConversationId = conversation.id
        }
        if (messages.none { it.role == "user" && it.text == task.prompt }) {
            ConversationStore.append(messages, ChatMessage(System.currentTimeMillis(), "user", task.prompt))
            persistActiveConversation()
        }
        launchGeneration(task.prompt)
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101713)) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.symbol, fontWeight = FontWeight.Bold) },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab == AppTab.CHAT) {
                    OutlinedButton(
                        onClick = { showConversationList = !showConversationList },
                        enabled = !isGenerating,
                        modifier = Modifier.height(40.dp)
                    ) { Text(if (showConversationList) "← Chat" else "☰") }
                    Spacer(Modifier.size(8.dp))
                }
                Box(
                    modifier = Modifier.size(38.dp).background(Color(0xFF173D2B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = Color(0xFF54E69C), fontWeight = FontWeight.Black)
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("Mikael AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("INTELIGÊNCIA LOCAL", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8BE7B7))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (activeEngineReady) "● LOCAL" else "○ OFFLINE",
                    color = if (activeEngineReady) Color(0xFF54E69C) else Color(0xFFB8C6BD),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab-transition"
            ) { selected ->
                when (selected) {
                    AppTab.CHAT -> if (showConversationList) {
                        ConversationHistoryScreen(
                            conversations = conversations,
                            activeConversationId = activeConversationId,
                            onNewConversation = { startNewConversation() },
                            onOpenConversation = { openConversation(it) }
                        )
                    } else ChatScreen(
                        messages = messages,
                        input = input,
                        onInputChange = { input = it },
                        engineReady = activeEngineReady,
                        engineStatus = activeEngineStatus,
                        isGenerating = isGenerating,
                        pendingTask = pendingTask,
                        onResumeTask = { resumePendingTask() },
                        onCancel = {
                            if (selectedCatalogModelId == "mikael-base") LocalInferenceEngine.cancel()
                            else LlamaInferenceEngine.cancel()
                            PendingTaskStore.clear(context)
                            pendingTask = null
                            context.stopService(Intent(context, ThinkingService::class.java))
                        },
                        onSend = {
                            val prompt = input.trim()
                            if (prompt.isNotEmpty() && activeEngineReady && !isGenerating) {
                                input = ""
                                ConversationStore.append(messages, ChatMessage(System.currentTimeMillis(), "user", prompt))
                                val memoryChanged = UserMemoryStore.rememberFromUserMessage(context, prompt) ||
                                    UserMemoryStore.forgetFromUserMessage(context, prompt)
                                if (memoryChanged) {
                                    userMemoryItems = UserMemoryStore.load(context)
                                    userMemoryCount = userMemoryItems.size
                                }
                                persistActiveConversation()
                                launchGeneration(prompt)

                            } else if (!activeEngineReady) {
                                android.widget.Toast.makeText(context, activeEngineStatus, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    AppTab.MODELS -> ModelCatalogScreen(
                        installed = catalogInstalled,
                        progress = catalogProgress,
                        errors = catalogErrors,
                        selectedModelId = selectedCatalogModelId,
                        mikaelAvailable = !modelPath.isNullOrBlank() && File(modelPath!!).isFile,
                        onSelect = { model ->
                            selectedCatalogModelId = model.id
                            preferences.selectedCatalogModelId = model.id
                            tab = AppTab.CHAT
                        },
                        onImportMkai = { modelPicker.launch(arrayOf("application/octet-stream")) },
                        onTrainMkai = {
                            val trainingUrl = Uri.parse(
                                "https://github.com/mikael8367/Mikael-AI-Android/edit/fix/ai-runtime-stability/training/corpus.txt"
                            )
                            context.startActivity(Intent(Intent.ACTION_VIEW, trainingUrl))
                        },
                        onInstall = { model ->
                            catalogErrors = catalogErrors - model.id
                            catalogProgress = catalogProgress + (model.id to 0)
                            scope.launch {
                                try {
                                    if (model.engine == ModelEngine.MKAI) {
                                        val file = MikaelModelInstaller.install(context, model) { percent ->
                                            catalogProgress = catalogProgress + (model.id to percent)
                                        }
                                        preferences.modelPath = file.absolutePath
                                        modelPath = file.absolutePath
                                        selectedCatalogModelId = model.id
                                        preferences.selectedCatalogModelId = model.id
                                        installedModels = listInstalledModels(context)
                                        tab = AppTab.CHAT
                                    } else {
                                        val file = ModelInstaller.install(context, model) { percent ->
                                            catalogProgress = catalogProgress + (model.id to percent)
                                        }
                                        val sharedModels = ModelCatalog.models
                                            .filter { model.fileName != null && it.fileName == model.fileName }
                                            .associate { it.id to file }
                                            .ifEmpty { mapOf(model.id to file) }
                                        catalogInstalled = catalogInstalled + sharedModels
                                    }
                                    catalogProgress = catalogProgress - model.id
                                    catalogErrors = catalogErrors - model.id
                                } catch (error: Exception) {
                                    catalogProgress = catalogProgress - model.id
                                    catalogErrors = catalogErrors + (model.id to (error.message ?: "Falha no download."))
                                }
                            }
                        },
                        onDelete = { model ->
                            scope.launch(Dispatchers.IO) {
                                val file = catalogInstalled[model.id]
                                val sharedIds = ModelCatalog.models
                                    .filter { model.fileName != null && it.fileName == model.fileName }
                                    .map { it.id }
                                    .toSet()
                                    .ifEmpty { setOf(model.id) }
                                if (file != null) {
                                    file.delete()
                                    File(file.parentFile, "${file.name}.sha256").delete()
                                }
                                withContext(Dispatchers.Main) {
                                    catalogInstalled = catalogInstalled.filterKeys { it !in sharedIds }
                                    if (selectedCatalogModelId in sharedIds) {
                                        selectedCatalogModelId = "mikael-base"
                                        preferences.selectedCatalogModelId = "mikael-base"
                                    }
                                }
                            }
                        }
                    )
                    AppTab.GITHUB -> GithubConnectorScreen(
                        clientId = githubClientId,
                        onClientIdChange = {
                            githubClientId = it
                            preferences.githubClientId = it
                        },
                        onContextReady = { repositoryContext ->
                            githubContext = repositoryContext
                            tab = AppTab.CHAT
                            android.widget.Toast.makeText(context, "README adicionado ao contexto do chat.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        userMemoryEnabled = userMemoryEnabled,
                        userMemoryCount = userMemoryCount,
                        userMemoryItems = userMemoryItems,
                        onUserMemoryEnabledChange = { enabled ->
                            UserMemoryStore.setEnabled(context, enabled)
                            userMemoryEnabled = enabled
                        },
                        onClearUserMemory = {
                            UserMemoryStore.clear(context)
                            userMemoryItems = emptyList()
                            userMemoryCount = 0
                        },
                        onRemoveUserMemory = { item ->
                            UserMemoryStore.remove(context, item)
                            userMemoryItems = UserMemoryStore.load(context)
                            userMemoryCount = userMemoryItems.size
                        },
                        allowOnlineFeatures = allowOnlineFeatures,
                        onAllowOnlineFeaturesChange = {
                            allowOnlineFeatures = it
                            preferences.allowOnlineFeatures = it
                        },
                        memoryLimit = memoryLimit,
                        contextTokens = contextTokens,
                        availableBytes = MemoryBudget.availableBytes(activityManager),
                        onMemoryChange = {
                            memoryLimit = it
                            preferences.memoryLimitMiB = it
                        },
                        onContextChange = {
                            contextTokens = it
                            preferences.contextTokens = it
                        },
                        onClearHistory = {
                            messages.clear()
                            conversations.clear()
                            activeConversationId = java.util.UUID.randomUUID().toString()
                            ConversationStore.clear(context)
                        }
                    )
                }
            }
        }
    }
}

private const val MAX_MODEL_FILE_BYTES = 240_000_040L

private fun copyModelToPrivateStorage(context: Context, uri: Uri): String {
    val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
    } ?: "modelo.mkai"
    require(displayName.lowercase().endsWith(".mkai")) {
        "Selecione um arquivo de pesos autorais com extensão .mkai."
    }
    val directory = File(context.filesDir, "models").apply { check(mkdirs() || isDirectory) }
    val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val uniqueName = "${System.currentTimeMillis()}_$safeName"
    val destination = File(directory, uniqueName)
    val temporary = File(directory, "$uniqueName.importing")
    try {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "O Android não conseguiu abrir o arquivo selecionado." }
            temporary.outputStream().use { output ->
                val buffer = ByteArray(128 * 1024)
                var totalBytes = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    totalBytes += read
                    require(totalBytes <= MAX_MODEL_FILE_BYTES) {
                        "O modelo excede o limite de tamanho permitido para importação."
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        require(temporary.length() >= 1024L) { "O arquivo MKAI está vazio ou incompleto." }
        require(MikaelModelInstaller.isValidMkai(temporary)) {
            "Arquivo MKAI inválido, incompleto ou incompatível com o motor do aplicativo."
        }
        check(temporary.renameTo(destination)) { "Não foi possível concluir a cópia do modelo." }
        return destination.absolutePath
    } catch (error: Exception) {
        temporary.delete()
        throw error
    }
}

@Composable
private fun ChatScreen(
    messages: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    engineReady: Boolean,
    engineStatus: String,
    isGenerating: Boolean,
    pendingTask: PendingAiTask?,
    onResumeTask: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        if (pendingTask != null && !isGenerating) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF26382D))) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tarefa interrompida", fontWeight = FontWeight.SemiBold, color = Color(0xFFB8E8CA))
                    Text(pendingTask.prompt.take(120), color = Color(0xFFB8C6BD), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onResumeTask, enabled = engineReady) { Text(if (engineReady) "Retomar tarefa" else "Carregando modelo…") }
                }
            }
        }
        if (!engineReady) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2B22))
            ) {
                Text(engineStatus, modifier = Modifier.padding(14.dp), color = Color(0xFFB8E8CA))
            }
        }
        if (messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Mikael AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Sua IA de programação, direto no aparelho.",
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color(0xFF9EAAA2),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Instale um modelo na aba Modelos para começar.",
                        modifier = Modifier.padding(top = 12.dp, start = 28.dp, end = 28.dp),
                        color = Color(0xFF8BE7B7),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message -> MessageBubble(message) }
                if (isGenerating) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("  Mikael AI está pensando...", color = Color(0xFF9EAAA2))
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFF26332B))
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Pergunte sobre programação...") },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
                enabled = !isGenerating
            )
            Spacer(Modifier.size(8.dp))
            if (isGenerating) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.height(54.dp)) { Text("Parar") }
            } else {
                Button(onClick = onSend, enabled = input.isNotBlank(), modifier = Modifier.height(54.dp)) { Text("Enviar") }
            }
        }
    }
}

@Composable
private fun ConversationHistoryScreen(
    conversations: List<ChatConversation>,
    activeConversationId: String,
    onNewConversation: () -> Unit,
    onOpenConversation: (ChatConversation) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Conversas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Seu histórico salvo neste aparelho", color = Color(0xFF9EAAA2))
            }
            Button(onClick = onNewConversation) { Text("+ Nova") }
        }
        if (conversations.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Suas conversas aparecerão aqui depois da primeira mensagem.", color = Color(0xFF9EAAA2), textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    Card(
                        onClick = { onOpenConversation(conversation) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (conversation.id == activeConversationId) Color(0xFF1B3829) else Color(0xFF151D19)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(conversation.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(
                                conversation.messages.lastOrNull()?.text.orEmpty().take(100),
                                color = Color(0xFF9EAAA2),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                            Text(
                                "${conversation.messages.size} mensagens",
                                color = Color(0xFF8BE7B7),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val fromUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (fromUser) Color(0xFF1B5B3A) else Color(0xFF1B2420),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Text(message.text, modifier = Modifier.padding(14.dp), color = Color(0xFFF1F5F2))
        }
    }
}

private fun listInstalledModels(context: Context): List<File> {
    return File(context.filesDir, "models")
        .listFiles()
        ?.filter { it.isFile && it.extension.equals("mkai", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

@Composable
private fun ModelsScreen(
    modelPath: String?,
    installedModels: List<File>,
    engineStatus: String,
    engineReady: Boolean,
    onImport: () -> Unit,
    onSelect: (String) -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Motor e modelos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "O motor próprio aceita somente arquivos .mkai. Modelos GGUF e pesos de terceiros não são compatíveis.",
            color = Color(0xFF9EAAA2)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151D19)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MODELO ATIVO", color = Color(0xFF8BE7B7), style = MaterialTheme.typography.labelSmall)
                Text(modelPath?.let { File(it).name } ?: "Nenhum modelo selecionado", fontWeight = FontWeight.SemiBold)
                Text(engineStatus, color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
                if (modelPath != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (engineReady) OutlinedButton(onClick = onUnload) { Text("Descarregar") }
                        else OutlinedButton(onClick = onLoad) { Text("Carregar") }
                        OutlinedButton(onClick = { onDelete(modelPath) }) { Text("Excluir ativo") }
                    }
                }
            }
        }
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("Importar modelo próprio (.mkai)")
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF173024)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Mikael AI Base", fontWeight = FontWeight.Bold, color = Color(0xFF8BE7B7))
                        Text("Motor e pipeline de treinamento autoral implementados. O corpus inicial de tarefas simples já está no repositório; execute o treinamento no GitHub Actions e importe o arquivo .mkai gerado.")
                        Text("Estado: corpus inicial disponível; primeiro treinamento ainda precisa ser executado. O formato atual usa bytes UTF-8 e ainda não integra o BPE MKTOK.", color = Color(0xFFFFD28A), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text("Modelos instalados neste aparelho", fontWeight = FontWeight.Bold) }
            if (installedModels.isEmpty()) {
                item { Text("Nenhum modelo .mkai instalado. Execute o workflow Treinar modelo próprio Mikael AI e importe o artefato gerado. Veja training/README.md.", color = Color(0xFF9EAAA2)) }
            } else {
                items(installedModels, key = { it.absolutePath }) { file ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151D19))) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${file.length() / (1024L * 1024L)} MiB",
                                    color = Color(0xFF9EAAA2),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (file.absolutePath == modelPath) {
                                Text("Ativo", color = Color(0xFF54E69C), fontWeight = FontWeight.Bold)
                            } else {
                                OutlinedButton(onClick = { onSelect(file.absolutePath) }) { Text("Usar") }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "O tamanho do arquivo não representa toda a RAM necessária. O contexto e os buffers também consomem memória.",
                    color = Color(0xFF9EAAA2),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    userMemoryEnabled: Boolean,
    userMemoryCount: Int,
    userMemoryItems: List<UserMemoryItem>,
    onUserMemoryEnabledChange: (Boolean) -> Unit,
    onClearUserMemory: () -> Unit,
    onRemoveUserMemory: (UserMemoryItem) -> Unit,
    allowOnlineFeatures: Boolean,
    onAllowOnlineFeaturesChange: (Boolean) -> Unit,
    memoryLimit: Int,
    contextTokens: Int,
    availableBytes: Long,
    onMemoryChange: (Int) -> Unit,
    onContextChange: (Int) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Configurações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Orçamento de memória", fontWeight = FontWeight.SemiBold)
            Text("Memória disponível agora: ${MemoryBudget.mib(availableBytes)} MiB", color = Color(0xFF9EAAA2))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(512, 768, 1024).forEach { value ->
                    FilterChip(
                        selected = memoryLimit == value,
                        onClick = { onMemoryChange(value) },
                        label = { Text("$value MiB") }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1536, 2048).forEach { value ->
                    FilterChip(
                        selected = memoryLimit == value,
                        onClick = { onMemoryChange(value) },
                        label = { Text("$value MiB") }
                    )
                }
            }
            Text(
                "Esse valor é um orçamento estimado para o motor, não um limite absoluto da memória do Android.",
                color = Color(0xFF9EAAA2),
                style = MaterialTheme.typography.bodySmall
            )
        }
        HorizontalDivider(color = Color(0xFF26332B))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Memória do usuário", fontWeight = FontWeight.SemiBold)
            Text("$userMemoryCount lembranças salvas neste aparelho. Use \"Lembre: ...\" para guardar e \"Esqueça: ...\" para remover uma lembrança pelo chat.", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
            userMemoryItems.groupBy { it.topic }.toSortedMap().forEach { (topic, entries) ->
                Text(topic, fontWeight = FontWeight.SemiBold, color = Color(0xFF8BE7B7))
                entries.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.text, modifier = Modifier.weight(1f), color = Color(0xFFB8C6BD), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onRemoveUserMemory(item) }) { Text("Remover") }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Usar memória nas respostas", modifier = Modifier.weight(1f))
                Switch(checked = userMemoryEnabled, onCheckedChange = onUserMemoryEnabledChange)
            }
            OutlinedButton(onClick = onClearUserMemory, enabled = userMemoryCount > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Apagar todas as lembranças")
            }
        }
        HorizontalDivider(color = Color(0xFF26332B))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Serviços online", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Permitir recursos que usam internet")
                    Text("Desativado por padrão. Ativar não envia conversas automaticamente; cada integração deverá pedir confirmação antes de compartilhar conteúdo.", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = allowOnlineFeatures, onCheckedChange = onAllowOnlineFeaturesChange)
            }
        }
        HorizontalDivider(color = Color(0xFF26332B))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tamanho do contexto", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(512, 1024).forEach { value ->
                    FilterChip(
                        selected = contextTokens == value,
                        onClick = { onContextChange(value) },
                        label = { Text("$value") }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(2048, 4096).forEach { value ->
                    FilterChip(
                        selected = contextTokens == value,
                        onClick = { onContextChange(value) },
                        label = { Text("$value") }
                    )
                }
            }
        }
        HorizontalDivider(color = Color(0xFF26332B))
        OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) {
            Text("Apagar histórico local")
        }
        Text(
            "A conversa com modelos instalados continua local. Recursos online serão identificados separadamente e deverão explicar quais dados serão enviados antes de pedir sua confirmação.",
            color = Color(0xFF9EAAA2)
        )
    }
}

private fun loadCatalogInstalled(context: Context): Map<String, File> =
    ModelCatalog.models.mapNotNull { model ->
        ModelInstaller.installedFile(context, model)?.let { model.id to it }
    }.toMap()
