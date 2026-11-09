package com.mikael.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun OfflineLogsScreen(
    context: Context,
    onLog: (String, String) -> Unit
) {
    val entries = remember { mutableStateListOf<String>().apply { addAll(AppLogStore.read(context)) } }

    LaunchedEffect(Unit) {
        while (true) {
            val latest = AppLogStore.read(context)
            if (latest != entries.toList()) {
                entries.clear()
                entries.addAll(latest)
            }
            delay(600)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Logs do aplicativo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Registro local das ações, toques, mudanças de configuração, carregamento do modelo e erros. Atualiza automaticamente.",
            color = Color(0xFF9EAAA2),
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val text = entries.joinToString("\n").ifBlank { "Nenhum log registrado." }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Mikael AI Logs", text))
                    Toast.makeText(context, "Logs copiados", Toast.LENGTH_SHORT).show()
                    onLog("INFO", "Logs copiados para a área de transferência")
                },
                modifier = Modifier.weight(1f)
            ) { Text("Copiar tudo") }
            OutlinedButton(
                onClick = {
                    AppLogStore.clear(context)
                    entries.clear()
                    onLog("WARN", "Histórico de logs limpo pelo usuário")
                }
            ) { Text("Limpar") }
        }
        Row {
            Text("Registros: ${entries.size}/2000", color = Color(0xFF8BE7B7), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text("Armazenamento local", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.labelSmall)
        }
        if (entries.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151D19))) {
                Text("Nenhuma atividade registrada ainda.", Modifier.padding(16.dp), color = Color(0xFF9EAAA2))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries.asReversed()) { line ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151D19))) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                line,
                                Modifier.weight(1f).padding(vertical = 5.dp),
                                color = when {
                                    line.contains("[ERROR]") -> Color(0xFFFF8A80)
                                    line.contains("[WARN]") -> Color(0xFFFFD18A)
                                    else -> Color(0xFFD6E2DA)
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Mikael AI Log", line))
                                Toast.makeText(context, "Registro copiado", Toast.LENGTH_SHORT).show()
                                onLog("INFO", "Registro individual copiado")
                            }) { Text("Copiar") }
                        }
                    }
                }
            }
        }
    }
}
