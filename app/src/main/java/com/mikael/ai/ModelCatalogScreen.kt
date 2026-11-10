package com.mikael.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    busy: Boolean,
    onSelect: (CatalogModel) -> Unit,
    onInstall: (CatalogModel) -> Unit,
    onCancelInstall: (CatalogModel) -> Unit,
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
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text("Modelos disponíveis", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Qwen2.5-Coder é o único modelo de conversa do Mikael AI.", color = Color(0xFF9EAAA2))
                Text(
                    "Instale um modelo e toque em Usar modelo. O carregamento acontece automaticamente.",
                    color = Color(0xFF9EAAA2),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        items(ModelCatalog.models, key = { it.id }) { model ->
            val file = installed[model.id]
            val percent = progress[model.id]
            val isMikael = model.engine == ModelEngine.MKAI
            val isInstalled = if (isMikael) mikaelAvailable else file != null
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151D19)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(model.name, fontWeight = FontWeight.Bold)
                            Text(model.family, color = Color(0xFF8BE7B7), style = MaterialTheme.typography.labelSmall)
                        }
                        if (isInstalled) {
                            Text("INSTALADO", color = Color(0xFF54E69C), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(model.description, color = Color(0xFFB8C6BD), style = MaterialTheme.typography.bodySmall)
                    Text("Licença: ${model.license}", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.labelSmall)

                    if (isMikael) {
                        Text(
                            if (mikaelAvailable) "Checkpoint MKAI instalado neste aparelho."
                            else "O checkpoint autoral MKAI v1 já está publicado. Toque em Baixar e instalar para usar. Também é possível treinar uma versão futura com seu próprio corpus.",
                            color = if (mikaelAvailable) Color(0xFF54E69C) else Color(0xFFFFD28A),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (percent != null) {
                            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                            Text("Baixando Mikael AI: $percent%", color = Color(0xFF8BE7B7))
                            OutlinedButton(onClick = { onCancelInstall(model) }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar download") }
                        }
                        errors[model.id]?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { confirmationModel = model },
                            enabled = percent == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (percent != null) "Baixando..." else "Baixar e instalar Mikael AI")
                        }
                        OutlinedButton(onClick = onTrainMkai, modifier = Modifier.fillMaxWidth()) {
                            Text("Treinar uma nova versão do Mikael AI")
                        }
                        OutlinedButton(onClick = onImportMkai, modifier = Modifier.fillMaxWidth()) {
                            Text("Importar arquivo .mkai manualmente")
                        }
                        if (mikaelAvailable) {
                            OutlinedButton(
                                onClick = { onSelect(model) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedModelId != model.id && !busy
                            ) {
                                Text(if (selectedModelId == model.id) "Mikael AI selecionado" else "Usar Mikael AI")
                            }
                        }
                    } else {
                        Text(
                            "Tamanho aproximado: ${model.approximateBytes / 1_000_000} MB • Q4_K_M",
                            color = Color(0xFF9EAAA2),
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (percent != null) {
                            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                            Text("Baixando: $percent%", color = Color(0xFF8BE7B7))
                            OutlinedButton(onClick = { onCancelInstall(model) }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar download") }
                        }
                        errors[model.id]?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (file != null) {
                                Button(
                                    onClick = { onSelect(model) },
                                    enabled = selectedModelId != model.id && !busy,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (selectedModelId == model.id) "Selecionado" else "Usar modelo")
                                }
                                OutlinedButton(
                                    onClick = { deleteModelConfirmation = model },
                                    modifier = Modifier.weight(1f),
                                    enabled = !busy
                                ) { Text("Desinstalar") }
                            } else {
                                Button(
                                    onClick = { confirmationModel = model },
                                    enabled = percent == null && !busy,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (percent != null) "Baixando..." else "Instalar modelo")
                                }
                                if (percent != null) {
                                    OutlinedButton(onClick = { onCancelInstall(model) }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                                }
                            }
                        }
                        Text("Fonte: Hugging Face • ${model.sourceUrl}", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.size(8.dp))
            Text(
                "O Mikael AI mantém seu nome e sua interface, mas utiliza Qwen2.5-Coder como motor de conversa. Os pesos do modelo são de terceiros e são baixados separadamente do APK.",
                color = Color(0xFF9EAAA2),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    deleteModelConfirmation?.let { model ->
        AlertDialog(
            onDismissRequest = { deleteModelConfirmation = null },
            title = { Text("Desinstalar modelo?") },
            text = { Text("Os pesos de ${model.name} serão removidos do armazenamento interno. Para usar novamente, será necessário baixar o arquivo outra vez. Histórico e memória não serão apagados.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(model)
                    deleteModelConfirmation = null
                }, enabled = !busy) { Text("Desinstalar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteModelConfirmation = null }) { Text("Cancelar") }
            }
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
            contextTokens = if (model.engine == ModelEngine.MKAI) contextTokens.coerceIn(16, 8192) else contextTokens.coerceIn(128, 2048)
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
                    if (model.license == "Licença não declarada") {
                        Text("Os termos de redistribuição deste peso autoral ainda não foram definidos. A distribuição a terceiros deve aguardar a licença do autor.", color = Color(0xFFFFD28A), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Fonte: ${model.sourceUrl}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (estimate.allowed) "RAM estimada pelo orçamento do app: ${formatModelBytes(estimate.estimatedBytes)}. É uma estimativa, não um benchmark."
                        else "Aviso de RAM: ${estimate.reason ?: "Memória disponível insuficiente."} O download pode ser instalado, mas o carregamento poderá ser recusado.",
                        color = if (estimate.allowed) Color(0xFF8BE7B7) else Color(0xFFFFD28A),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!hasSpace) {
                        Text("Armazenamento insuficiente para baixar e validar este arquivo. Libere espaço antes de continuar.", color = MaterialTheme.colorScheme.error)
                    }
                    Text("Nenhuma conversa ou arquivo pessoal será enviado. O app só baixa o modelo escolhido após sua confirmação.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = model
                        confirmationModel = null
                        onInstall(selected)
                    },
                    enabled = hasSpace && !busy
                ) { Text("Baixar e instalar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmationModel = null }) { Text("Cancelar") }
            }
        )
    }
}

private fun formatModelBytes(bytes: Long): String {
    if (bytes < 1_000_000L) return "${bytes / 1_000} KB"
    return String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
}
