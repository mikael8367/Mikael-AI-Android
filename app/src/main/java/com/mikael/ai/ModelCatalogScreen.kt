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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onSelect: (CatalogModel) -> Unit,
    onInstall: (CatalogModel) -> Unit,
    onImportMkai: () -> Unit,
    onTrainMkai: () -> Unit,
    onDelete: (CatalogModel) -> Unit
) {
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
                Text("Mikael AI autoral e três modelos GGUF opcionais.", color = Color(0xFF9EAAA2))
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
                            else "O primeiro checkpoint precisa ser treinado e publicado uma vez. Depois, você poderá baixá-lo diretamente por este botão.",
                            color = if (mikaelAvailable) Color(0xFF54E69C) else Color(0xFFFFD28A),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (percent != null) {
                            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                            Text("Baixando Mikael AI: $percent%", color = Color(0xFF8BE7B7))
                        }
                        errors[model.id]?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { onInstall(model) },
                            enabled = percent == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (percent != null) "Baixando..." else "Baixar e instalar Mikael AI")
                        }
                        OutlinedButton(onClick = onTrainMkai, modifier = Modifier.fillMaxWidth()) {
                            Text("Adicionar corpus e treinar no GitHub")
                        }
                        OutlinedButton(onClick = onImportMkai, modifier = Modifier.fillMaxWidth()) {
                            Text("Importar arquivo .mkai manualmente")
                        }
                        if (mikaelAvailable) {
                            OutlinedButton(
                                onClick = { onSelect(model) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedModelId != model.id
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
                                    enabled = selectedModelId != model.id,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (selectedModelId == model.id) "Selecionado" else "Usar modelo")
                                }
                                OutlinedButton(
                                    onClick = { onDelete(model) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Desinstalar") }
                            } else {
                                Button(
                                    onClick = { onInstall(model) },
                                    enabled = percent == null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (percent != null) "Baixando..." else "Instalar modelo")
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
                "Os modelos GGUF de terceiros são opcionais. O Mikael AI autoral usa o formato MKAI próprio.",
                color = Color(0xFF9EAAA2),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
