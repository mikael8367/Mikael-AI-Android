package com.mikael.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun ModelCatalogScreen(
    installed: Map<String, File>,
    progress: Map<String, Int>,
    errors: Map<String, String>,
    selectedModelId: String,
    mikaelAvailable: Boolean,
    teamReady: Boolean,
    teamInstallStatus: String,
    teamInstalling: Boolean,
    busy: Boolean,
    onSelect: (CatalogModel) -> Unit,
    onSelectTeam: () -> Unit,
    onInstall: (CatalogModel) -> Unit,
    onInstallTeam: () -> Unit,
    onCancelInstall: (CatalogModel) -> Unit,
    onCancelTeamInstall: () -> Unit,
    onImportMkai: () -> Unit,
    onTrainMkai: () -> Unit,
    onDelete: (CatalogModel) -> Unit,
    availableStorageBytes: Long,
    availableMemoryBytes: Long,
    memoryLimitMiB: Int,
    contextTokens: Int
) {
    var confirmationModel by remember { mutableStateOf<CatalogModel?>(null) }
    var deleteModelConfirmation by remember { mutableStateOf<CatalogModel?>(null) }
    var confirmTeamInstall by remember { mutableStateOf(false) }
    val missingTeamModels = ModelCatalog.models.filter { model ->
        if (model.engine == ModelEngine.MKAI) !mikaelAvailable else installed[model.id] == null
    }
    val teamDownloadBytes = missingTeamModels.sumOf { it.approximateBytes }
    val teamRequiredSpace = teamDownloadBytes + 64L * 1024L * 1024L

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Modelos disponíveis", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Escolha um modelo individual ou use a nova equipe com histórico compartilhado.", color = Color(0xFF9EAAA2))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF173024)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mikael AI — 4 modelos", fontWeight = FontWeight.Bold, color = Color(0xFF8BE7B7))
                    Text(
                        "Uma conversa única. Mikael AI, Qwen 2.5, SmolLM2 e TinyLlama contribuem em sequência, recebendo o histórico compartilhado das falas anteriores. Na última etapa, um modelo revisa as contribuições e consolida uma resposta só.",
                        color = Color(0xFFE1EAE4), style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Execução 100% local. Um modelo é carregado por vez; colaboração sequencial não significa execução simultânea.",
                        color = Color(0xFFB8C6BD), style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (teamReady && missingTeamModels.isEmpty()) "Todos os quatro modelos estão instalados."
                        else if (teamReady) "Equipe parcial pronta: ${4 - missingTeamModels.size}/4 modelos instalados. Os modelos disponíveis já podem responder."
                        else "Instale o Mikael AI e pelo menos um modelo GGUF. Faltam ${missingTeamModels.size} modelo(s). Download restante: ${formatModelBytes(teamDownloadBytes)}.",
                        color = if (teamReady) Color(0xFF54E69C) else Color(0xFFFFD28A), style = MaterialTheme.typography.labelSmall
                    )
                    if (teamInstallStatus.isNotBlank()) Text(teamInstallStatus, color = Color(0xFF8BE7B7), style = MaterialTheme.typography.bodySmall)
                    if (teamInstalling) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = onCancelTeamInstall, modifier = Modifier.fillMaxWidth()) { Text("Cancelar instalação da equipe") }
                    } else if (!teamReady) {
                        Button(onClick = { confirmTeamInstall = true }, enabled = !busy && missingTeamModels.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("Baixar modelos faltantes")
                        }
                    } else {
                        OutlinedButton(onClick = { confirmTeamInstall = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Verificar integridade dos quatro modelos")
                        }
                    }
                    Button(
                        onClick = onSelectTeam,
                        enabled = teamReady && selectedModelId != ModelCatalog.TEAM_MODEL_ID && !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (selectedModelId == ModelCatalog.TEAM_MODEL_ID) "Equipe selecionada" else "Usar Mikael AI — 4 modelos") }
                    Text(
                        "O TinyLlama 1.1B é o participante mais pesado. O MKAI v1 tem apenas 512 bytes de contexto e pode ser ignorado em pedidos longos. Pedidos acima de 3.000 bytes precisam ser divididos. A memória é verificada antes de cada carga; se um modelo não couber, ele será marcado como falha. A referência pública do TinyLlama Q4_K_M estima até ~3,17 GB de RAM em certas condições; o orçamento pode chegar a 3 GB em aparelhos maiores, mas a memória livre real do Galaxy A12 normalmente não alcança essa exigência; o app o recusará quando o orçamento seguro for insuficiente. O contexto GGUF começa em 1024 e cresce até 2048 conforme o prompt. As cinco etapas em CPU podem demorar, consumir bateria e aquecer o aparelho.",
                        color = Color(0xFFB8C6BD), style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        items(ModelCatalog.models, key = { it.id }) { model ->
            val file = installed[model.id]
            val percent = progress[model.id]
            val isMikael = model.engine == ModelEngine.MKAI
            val isInstalled = if (isMikael) mikaelAvailable else file != null
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151D19)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(model.name, fontWeight = FontWeight.Bold)
                            Text(model.family, color = Color(0xFF8BE7B7), style = MaterialTheme.typography.labelSmall)
                        }
                        if (isInstalled) Text("INSTALADO", color = Color(0xFF54E69C), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(model.description, color = Color(0xFFB8C6BD), style = MaterialTheme.typography.bodySmall)
                    Text("Licença: ${model.license}", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.labelSmall)

                    if (isMikael) {
                        Text(
                            if (mikaelAvailable) "Checkpoint MKAI instalado neste aparelho."
                            else "Checkpoint autoral experimental publicado. A licença de distribuição não está declarada.",
                            color = if (mikaelAvailable) Color(0xFF54E69C) else Color(0xFFFFD28A),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (percent != null) {
                            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                            Text("Baixando Mikael AI: $percent%", color = Color(0xFF8BE7B7))
                            OutlinedButton(onClick = { onCancelInstall(model) }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar download") }
                        }
                        errors[model.id]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Button(
                            onClick = { confirmationModel = model },
                            enabled = percent == null && !busy && !isInstalled,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isInstalled) "Mikael AI instalado" else if (percent != null) "Baixando..." else "Baixar e instalar Mikael AI") }
                        OutlinedButton(onClick = onTrainMkai, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Treinar uma nova versão do Mikael AI") }
                        OutlinedButton(onClick = onImportMkai, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Importar arquivo .mkai manualmente") }
                        if (mikaelAvailable) {
                            OutlinedButton(onClick = { onSelect(model) }, modifier = Modifier.fillMaxWidth(), enabled = selectedModelId != model.id && !busy) {
                                Text(if (selectedModelId == model.id) "Mikael AI selecionado" else "Usar Mikael AI")
                            }
                            OutlinedButton(onClick = { deleteModelConfirmation = model }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                                Text("Desinstalar checkpoint MKAI")
                            }
                        }
                    } else {
                        Text("Tamanho aproximado: ${formatModelBytes(model.approximateBytes)} • Q4_K_M", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.labelSmall)
                        if (percent != null) {
                            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                            Text("Baixando: $percent%", color = Color(0xFF8BE7B7))
                            OutlinedButton(onClick = { onCancelInstall(model) }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar download") }
                        }
                        errors[model.id]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (file != null) {
                                Button(onClick = { onSelect(model) }, enabled = selectedModelId != model.id && !busy, modifier = Modifier.weight(1f)) {
                                    Text(if (selectedModelId == model.id) "Selecionado" else "Usar modelo")
                                }
                                OutlinedButton(onClick = { deleteModelConfirmation = model }, modifier = Modifier.weight(1f), enabled = !busy) { Text("Desinstalar") }
                            } else {
                                Button(onClick = { confirmationModel = model }, enabled = percent == null && !busy, modifier = Modifier.weight(1f)) {
                                    Text(if (percent != null) "Baixando..." else "Instalar modelo")
                                }
                                if (percent != null) OutlinedButton(onClick = { onCancelInstall(model) }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                            }
                        }
                        Text("Fonte: Hugging Face • ${model.sourceUrl}", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (confirmTeamInstall) {
        AlertDialog(
            onDismissRequest = { confirmTeamInstall = false },
            title = { Text("Instalar equipe de quatro modelos?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (missingTeamModels.isEmpty()) {
                        Text("Todos os modelos parecem instalados. O app verificará os checksums e reparará arquivos inválidos, se necessário.")
                    } else {
                        Text("Modelos que faltam:")
                        missingTeamModels.forEach { model -> Text("• ${model.name}: ${formatModelBytes(model.approximateBytes)}") }
                    }
                    Text("Download estimado se os arquivos estiverem ausentes: ${formatModelBytes(teamDownloadBytes)}")
                    Text("Espaço livre: ${formatModelBytes(availableStorageBytes)}")
                    Text("Espaço necessário estimado: ${formatModelBytes(teamRequiredSpace)}")
                    Text("RAM disponível agora: ${formatModelBytes(availableMemoryBytes)} • orçamento configurado: $memoryLimitMiB MiB")
                    Text("TinyLlama 1.1B Q4_K_M tem referência pública de até ~3,17 GB de RAM em certas condições. No Galaxy A12, a verificação pode recusá-lo; ele não será contado como participante se não puder carregar.", color = Color(0xFFFFD28A), style = MaterialTheme.typography.bodySmall)
                    Text("Os arquivos serão baixados um por vez. Nenhum peso será baixado antes da confirmação. Se a verificação encontrar um arquivo corrompido, o app exigirá espaço para baixar somente esse arquivo novamente.")
                    Text("O uso de RAM é avaliado separadamente para cada modelo; ter espaço de armazenamento não garante que o TinyLlama consiga carregar.", color = Color(0xFFFFD28A), style = MaterialTheme.typography.bodySmall)
                    if (missingTeamModels.any { it.license == "Licença não declarada" }) {
                        Text("O checkpoint autoral Mikael AI está sem licença de distribuição declarada. O app informa essa condição e não o redistribui.", color = Color(0xFFFFD28A), style = MaterialTheme.typography.bodySmall)
                    }
                    if (availableStorageBytes <= teamRequiredSpace) Text("Armazenamento insuficiente. Libere espaço antes de continuar.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmTeamInstall = false; onInstallTeam() }, enabled = availableStorageBytes > teamRequiredSpace && !busy) {
                    Text("Verificar e instalar")
                }
            },
            dismissButton = { TextButton(onClick = { confirmTeamInstall = false }) { Text("Cancelar") } }
        )
    }

    deleteModelConfirmation?.let { model ->
        AlertDialog(
            onDismissRequest = { deleteModelConfirmation = null },
            title = { Text("Desinstalar modelo?") },
            text = { Text("Os pesos de ${model.name} serão removidos do armazenamento interno. Histórico e memória não serão apagados.") },
            confirmButton = {
                TextButton(onClick = { onDelete(model); deleteModelConfirmation = null }, enabled = !busy) { Text("Desinstalar") }
            },
            dismissButton = { TextButton(onClick = { deleteModelConfirmation = null }) { Text("Cancelar") } }
        )
    }

    confirmationModel?.let { model ->
        val oldFileBytes = installed[model.id]?.length() ?: 0L
        val requiredSpace = model.approximateBytes + oldFileBytes + 32L * 1024L * 1024L
        val hasSpace = availableStorageBytes > requiredSpace
        val estimate = MemoryBudget.check(
            availableBytes = availableMemoryBytes,
            selectedLimitMiB = memoryLimitMiB,
            modelBytes = model.approximateBytes,
            contextTokens = if (model.engine == ModelEngine.MKAI) contextTokens.coerceIn(16, 8192) else contextTokens.coerceIn(128, 2048),
            minimumEstimatedBytes = model.minimumRuntimeMemoryBytes
        )
        AlertDialog(
            onDismissRequest = { confirmationModel = null },
            title = { Text("Confirmar instalação") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(model.name, fontWeight = FontWeight.SemiBold)
                    Text("Download aproximado: ${formatModelBytes(model.approximateBytes)}")
                    Text("Espaço livre: ${formatModelBytes(availableStorageBytes)}")
                    Text("Espaço necessário estimado: ${formatModelBytes(requiredSpace)}")
                    Text("Licença: ${model.license}")
                    if (model.license == "Licença não declarada") Text("Os termos de redistribuição deste peso autoral ainda não foram definidos. A distribuição a terceiros deve aguardar a licença do autor.", color = Color(0xFFFFD28A), style = MaterialTheme.typography.bodySmall)
                    Text("Fonte: ${model.sourceUrl}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (estimate.allowed) "RAM estimada pelo orçamento do app: ${formatModelBytes(estimate.estimatedBytes)}. É uma estimativa, não um benchmark."
                        else "Aviso de RAM: ${estimate.reason ?: "Memória disponível insuficiente."} O download pode ser instalado, mas o carregamento poderá ser recusado.",
                        color = if (estimate.allowed) Color(0xFF8BE7B7) else Color(0xFFFFD28A),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!hasSpace) Text("Armazenamento insuficiente para baixar e validar este arquivo. Libere espaço antes de continuar.", color = MaterialTheme.colorScheme.error)
                    Text("Nenhuma conversa ou arquivo pessoal será enviado. O app só baixa o modelo escolhido após sua confirmação.")
                }
            },
            confirmButton = {
                TextButton(onClick = { val selected = model; confirmationModel = null; onInstall(selected) }, enabled = hasSpace && !busy) {
                    Text("Baixar e instalar")
                }
            },
            dismissButton = { TextButton(onClick = { confirmationModel = null }) { Text("Cancelar") } }
        )
    }
}

private fun formatModelBytes(bytes: Long): String {
    if (bytes < 1_000_000L) return "${bytes / 1_000} KB"
    return String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
}
