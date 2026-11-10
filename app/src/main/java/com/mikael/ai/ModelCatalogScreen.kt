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
    onInstall: (CatalogModel) -> Unit,
    onImportMkai: () -> Unit,
    onDelete: (CatalogModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Catálogo de modelos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Quatro modelos • downloads no armazenamento privado do app", color = Color(0xFF9EAAA2))
                Text("Os modelos GGUF podem ser baixados e armazenados. Para conversar com eles, a integração do runtime llama.cpp ainda precisa ser concluída.", color = Color(0xFFFFD28A), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(ModelCatalog.models, key = { it.id }) { model ->
            val file = installed[model.id]
            val percent = progress[model.id]
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
                        if (file != null) Text("INSTALADO", color = Color(0xFF54E69C), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(model.description, color = Color(0xFFB8C6BD), style = MaterialTheme.typography.bodySmall)
                    Text("Licença: ${model.license}", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.labelSmall)
                    if (model.engine == ModelEngine.MKAI) {
                        Text("Estado: aguardando corpus e pesos próprios. Nenhum peso de terceiros será usado no Mikael AI.", color = Color(0xFFFFD28A), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onImportMkai, modifier = Modifier.fillMaxWidth()) { Text("Importar modelo Mikael (.mkai)") }
                    } else {
                        Text("Tamanho aproximado: ${model.approximateBytes / 1_000_000} MB • Q4_K_M", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.labelSmall)
                        if (percent != null) {
                            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                            Text("Baixando: $percent%", color = Color(0xFF8BE7B7))
                        }
                        errors[model.id]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (file != null) {
                                OutlinedButton(onClick = { onDelete(model) }, modifier = Modifier.weight(1f)) { Text("Desinstalar") }
                            } else {
                                Button(onClick = { onInstall(model) }, enabled = percent == null, modifier = Modifier.weight(1f)) {
                                    Text(if (percent != null) "Baixando..." else "Instalar")
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
            Text("Os downloads exigem conexão e espaço livre. A instalação valida o cabeçalho GGUF e o tamanho recebido; ela não ativa o modelo no chat.", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
        }
    }
}
