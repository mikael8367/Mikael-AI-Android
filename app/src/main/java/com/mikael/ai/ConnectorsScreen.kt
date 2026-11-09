package com.mikael.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp

private data class ConnectorSuggestion(
    val name: String,
    val category: String,
    val description: String,
    val setup: String
)

private val connectorSuggestions = listOf(
    ConnectorSuggestion("GitHub", "Desenvolvimento", "Repositórios, arquivos, branches, issues e pull requests.", "Conecte sua conta GitHub pelo site Mikael AI ou informe a URL HTTPS de um servidor MCP GitHub que você controla."),
    ConnectorSuggestion("Notion", "Produtividade", "Páginas, documentos, bases de dados e anotações.", "Use um servidor MCP compatível com Notion e gere o token nas configurações oficiais do serviço."),
    ConnectorSuggestion("Google Drive", "Arquivos", "Pesquisar e consultar documentos armazenados no Drive.", "Use um servidor MCP do Google Drive com OAuth configurado no próprio servidor."),
    ConnectorSuggestion("Slack", "Comunicação", "Pesquisar mensagens e consultar canais autorizados.", "Use um servidor MCP do Slack e conceda somente as permissões necessárias."),
    ConnectorSuggestion("PostgreSQL", "Banco de dados", "Consultar dados e esquemas de bancos PostgreSQL.", "Use um servidor MCP com acesso somente leitura sempre que possível."),
    ConnectorSuggestion("Arquivos", "Produtividade", "Consultar arquivos disponibilizados por um servidor MCP.", "Use um servidor MCP hospedado por você. Nunca exponha pastas privadas sem autenticação.")
)

@Composable
fun ConnectorsScreen(
    authenticated: Boolean,
    connectors: List<RemoteConnector>,
    busy: Boolean,
    status: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddMcp: (String, String, String) -> Unit,
    onToggle: (RemoteConnector, Boolean) -> Unit,
    onTest: (RemoteConnector) -> Unit,
    onDelete: (RemoteConnector) -> Unit,
    onOpenWebsite: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedSuggestion by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newEndpoint by remember { mutableStateOf("") }
    var newToken by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<RemoteConnector?>(null) }

    val query = search.trim()
    val visibleSuggestions = connectorSuggestions.filter {
        query.isBlank() || listOf(it.name, it.category, it.description).any { value -> value.contains(query, true) }
    }
    val visibleConnectors = connectors.filter {
        query.isBlank() || listOf(it.name, it.provider, it.account, it.endpoint).any { value -> value.contains(query, true) }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("‹ Voltar") }
            Text("Conectores", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else TextButton(onClick = onRefresh, enabled = authenticated) { Text("Atualizar") }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pesquisar conectores") },
            placeholder = { Text("GitHub, Notion, MCP...") },
            singleLine = true,
            enabled = !busy
        )
        if (!authenticated) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF202B25))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Conecte sua conta Mikael AI", style = MaterialTheme.typography.titleMedium)
                    Text("Entre no modo online em Ajustes para adicionar, testar e gerenciar conectores. As recomendações podem ser consultadas sem login.", color = Color(0xFFB8C6BD))
                    OutlinedButton(onClick = onOpenWebsite, modifier = Modifier.fillMaxWidth()) { Text("Abrir Mikael AI no navegador") }
                }
            }
        }
        if (status.isNotBlank()) {
            Text(status, color = Color(0xFF8BE7B7), style = MaterialTheme.typography.bodySmall)
        }
        Text("RECOMENDADOS", color = Color(0xFF8BE7B7), style = MaterialTheme.typography.labelSmall)
        if (visibleSuggestions.isEmpty()) {
            Text("Nenhuma recomendação corresponde à pesquisa.", color = Color(0xFF9EAAA2))
        }
        visibleSuggestions.forEach { suggestion ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151D19))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(suggestion.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        FilterChip(selected = false, onClick = {}, enabled = false, label = { Text(suggestion.category) })
                    }
                    Text(suggestion.description, color = Color(0xFFB8C6BD), style = MaterialTheme.typography.bodySmall)
                    Text(suggestion.setup, color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = {
                            selectedSuggestion = suggestion.name
                            newName = suggestion.name
                            newEndpoint = ""
                            newToken = ""
                            showAddDialog = true
                        },
                        enabled = authenticated && !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Configurar via MCP") }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF26332B))
        Text("CONECTORES ADICIONADOS", color = Color(0xFF8BE7B7), style = MaterialTheme.typography.labelSmall)
        if (visibleConnectors.isEmpty()) {
            Text(
                if (connectors.isEmpty()) "Nenhum conector adicionado ainda." else "Nenhum conector corresponde à pesquisa.",
                color = Color(0xFF9EAAA2)
            )
        }
        visibleConnectors.forEach { connector ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151D19))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(connector.name, style = MaterialTheme.typography.titleMedium)
                            Text("${connector.provider.uppercase()} • ${connector.account}", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = connector.enabled,
                            onCheckedChange = { onToggle(connector, it) },
                            enabled = authenticated && !busy
                        )
                    }
                    if (connector.endpoint.isNotBlank()) {
                        Text(connector.endpoint, color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { onTest(connector) }, enabled = authenticated && connector.enabled && !busy, modifier = Modifier.weight(1f)) { Text("Testar") }
                        OutlinedButton(onClick = { pendingDelete = connector }, enabled = authenticated && !busy, modifier = Modifier.weight(1f)) { Text("Remover") }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF26332B))
        Button(
            onClick = {
                selectedSuggestion = ""
                newName = ""
                newEndpoint = ""
                newToken = ""
                showAddDialog = true
            },
            enabled = authenticated && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("＋ Adicionar MCP personalizado") }
        Text("Os conectores MCP precisam de um endereço HTTPS público. O token é enviado ao servidor Mikael AI, que o armazena criptografado; nunca coloque tokens em mensagens do chat.", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showAddDialog = false },
            title = { Text(if (selectedSuggestion.isBlank()) "Adicionar MCP" else "Configurar ${selectedSuggestion}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nome do conector") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = newEndpoint, onValueChange = { newEndpoint = it }, label = { Text("URL HTTPS do servidor MCP") }, placeholder = { Text("https://servidor.exemplo/mcp") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = newToken, onValueChange = { newToken = it }, label = { Text("Token (opcional)") }, singleLine = true, enabled = !busy)
                    Text("O servidor será testado antes de ser salvo. Endereços locais e HTTP sem TLS não são aceitos.", color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddMcp(newName.trim(), newEndpoint.trim(), newToken.trim())
                        showAddDialog = false
                    },
                    enabled = authenticated && !busy && newName.trim().length >= 2 && newEndpoint.trim().startsWith("https://", true)
                ) { Text(if (busy) "Conectando..." else "Conectar e adicionar") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }, enabled = !busy) { Text("Cancelar") } }
        )
    }

    pendingDelete?.let { connector ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remover conector?") },
            text = { Text("O conector ${connector.name} será removido da sua conta Mikael AI.") },
            confirmButton = {
                Button(onClick = { onDelete(connector); pendingDelete = null }, enabled = !busy) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }
}
