package com.mikael.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private data class GithubDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

private data class GithubRepo(
    val fullName: String,
    val description: String,
    val isPrivate: Boolean,
    val defaultBranch: String
)

private sealed interface GithubPollResult {
    data class Authorized(val token: String) : GithubPollResult
    data object Pending : GithubPollResult
    data object SlowDown : GithubPollResult
    data object Expired : GithubPollResult
    data class Failed(val message: String) : GithubPollResult
}

private object GithubTokenStore {
    private const val PREFS = "github_secure_session"
    private const val TOKEN_KEY = "encrypted_access_token"
    private const val KEY_ALIAS = "mikael_ai_github_token_key"

    fun save(context: Context, token: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(TOKEN_KEY, payload).apply()
    }

    fun read(context: Context): String? = runCatching {
        val payload = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TOKEN_KEY, null) ?: return null
        val parts = payload.split(":", limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(TOKEN_KEY).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

private object GithubApi {
    private const val API = "https://api.github.com"

    private fun request(
        url: String,
        method: String = "GET",
        token: String? = null,
        body: String? = null,
        accept: String = "application/vnd.github+json"
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Mikael-AI-Android")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("message") }.getOrNull()
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "GitHub respondeu HTTP $status.")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun form(vararg values: Pair<String, String>): String =
        values.joinToString("&") {
            URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
        }

    suspend fun requestDeviceCode(clientId: String): GithubDeviceCode = withContext(Dispatchers.IO) {
        val json = JSONObject(
            request(
                "https://github.com/login/device/code",
                method = "POST",
                body = form("client_id" to clientId),
                accept = "application/json"
            )
        )
        GithubDeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUri = json.optString("verification_uri", "https://github.com/login/device"),
            expiresIn = json.optInt("expires_in", 900),
            interval = json.optInt("interval", 5)
        )
    }

    suspend fun pollDeviceToken(clientId: String, deviceCode: String): GithubPollResult =
        withContext(Dispatchers.IO) {
            val response = request(
                "https://github.com/login/oauth/access_token",
                method = "POST",
                body = form(
                    "client_id" to clientId,
                    "device_code" to deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                ),
                accept = "application/json"
            )
            val json = JSONObject(response)
            val token = json.optString("access_token")
            if (token.isNotBlank()) return@withContext GithubPollResult.Authorized(token)
            when (json.optString("error")) {
                "authorization_pending" -> GithubPollResult.Pending
                "slow_down" -> GithubPollResult.SlowDown
                "expired_token", "token_expired", "device_flow_expired" -> GithubPollResult.Expired
                "device_flow_disabled" -> GithubPollResult.Failed("Ative Enable Device Flow nas configurações do seu GitHub App.")
                "incorrect_client_credentials" -> GithubPollResult.Failed("Client ID incorreto. Use o Client ID, não o App ID.")
                "access_denied" -> GithubPollResult.Failed("Autorização recusada no GitHub.")
                else -> GithubPollResult.Failed(json.optString("error_description", "Falha na autorização GitHub."))
            }
        }

    suspend fun listRepositories(token: String): List<GithubRepo> = withContext(Dispatchers.IO) {
        val json = JSONArray(request("$API/user/repos?per_page=100&sort=updated&type=owner", token = token))
        buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val name = item.optString("full_name")
                if (name.isNotBlank()) {
                    add(
                        GithubRepo(
                            fullName = name,
                            description = item.optString("description"),
                            isPrivate = item.optBoolean("private"),
                            defaultBranch = item.optString("default_branch", "main")
                        )
                    )
                }
            }
        }
    }

    suspend fun readReadme(token: String, fullName: String): String = withContext(Dispatchers.IO) {
        require(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(fullName)) {
            "Nome de repositório inválido."
        }
        val encoded = fullName.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        val content = request(
            "$API/repos/$encoded/readme",
            token = token,
            accept = "application/vnd.github.raw+json"
        )
        content.take(24_000)
    }
}

@Composable
internal fun GithubConnectorScreen(
    clientId: String,
    onClientIdChange: (String) -> Unit,
    onContextReady: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(GithubTokenStore.read(context)) }
    var deviceCode by remember { mutableStateOf<GithubDeviceCode?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var isLoadingRepos by remember { mutableStateOf(false) }
    var isLoadingReadme by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (token == null) "Conecte sua conta para acessar seus repositórios públicos." else "GitHub conectado neste aparelho.") }
    var selectedRepo by remember { mutableStateOf<GithubRepo?>(null) }
    val repositories = remember { mutableStateListOf<GithubRepo>() }

    suspend fun refreshRepositories(currentToken: String) {
        isLoadingRepos = true
        try {
            repositories.clear()
            repositories.addAll(GithubApi.listRepositories(currentToken))
            status = if (repositories.isEmpty()) "Nenhum repositório público encontrado na sua conta." else "${repositories.size} repositório(s) carregado(s)."
        } catch (error: Exception) {
            status = error.message ?: "Não foi possível carregar os repositórios."
        } finally {
            isLoadingRepos = false
        }
    }

    LaunchedEffect(token) {
        if (token != null && repositories.isEmpty()) refreshRepositories(token!!)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Conector GitHub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Conecte sua conta para consultar repositórios e importar o README para o contexto do chat local.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )

        if (token == null) {
            OutlinedTextField(
                value = clientId,
                onValueChange = onClientIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub App Client ID") },
                placeholder = { Text("Iv1.xxxxxxxxxxxxxxxx") },
                singleLine = true
            )
            Text(
                "Use o Client ID do GitHub App com Enable Device Flow ativado. O acesso aos repositórios depende das permissões configuradas e da instalação do App. Não precisa de Client Secret.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            Button(
                onClick = {
                    val id = clientId.trim()
                    if (id.isBlank()) {
                        status = "Informe o Client ID do OAuth App."
                    } else {
                        isConnecting = true
                        status = "Solicitando código ao GitHub..."
                        scope.launch {
                            try {
                                val code = GithubApi.requestDeviceCode(id)
                                deviceCode = code
                                status = "Abra o GitHub e informe o código exibido abaixo."
                                var interval = code.interval.coerceAtLeast(5)
                                val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
                                while (System.currentTimeMillis() < deadline && token == null) {
                                    delay(interval * 1000L)
                                    when (val result = GithubApi.pollDeviceToken(id, code.deviceCode)) {
                                        is GithubPollResult.Authorized -> {
                                            GithubTokenStore.save(context, result.token)
                                            token = result.token
                                            deviceCode = null
                                            status = "Conta GitHub conectada com segurança."
                                            refreshRepositories(result.token)
                                            break
                                        }
                                        GithubPollResult.Pending -> status = "Aguardando você autorizar no GitHub..."
                                        GithubPollResult.SlowDown -> {
                                            interval += 5
                                            status = "GitHub pediu para aguardar um pouco mais..."
                                        }
                                        GithubPollResult.Expired -> {
                                            status = "O código expirou. Inicie a conexão novamente."
                                            deviceCode = null
                                            break
                                        }
                                        is GithubPollResult.Failed -> {
                                            status = result.message
                                            deviceCode = null
                                            break
                                        }
                                    }
                                }
                                if (token == null && deviceCode != null) {
                                    status = "O código expirou. Inicie a conexão novamente."
                                    deviceCode = null
                                }
                            } catch (error: Exception) {
                                status = error.message ?: "Não foi possível conectar ao GitHub."
                                deviceCode = null
                            } finally {
                                isConnecting = false
                            }
                        }
                    }
                },
                enabled = !isConnecting && clientId.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isConnecting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Conectar ao GitHub")
            }

            deviceCode?.let { code ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Seu código de autorização", fontWeight = FontWeight.SemiBold)
                        Text(code.userCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(code.userCode)) }) { Text("Copiar código") }
                            Button(onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri)))
                                }.onFailure { status = "Não foi possível abrir o navegador." }
                            }) { Text("Abrir GitHub") }
                        }
                    }
                }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("● CONECTADO", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Token salvo criptografado com Android Keystore.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { scope.launch { refreshRepositories(token!!) } },
                            enabled = !isLoadingRepos
                        ) { Text("Atualizar") }
                        OutlinedButton(onClick = {
                            GithubTokenStore.clear(context)
                            token = null
                            repositories.clear()
                            selectedRepo = null
                            status = "Conta desconectada deste aparelho. Para revogar o acesso, remova a autorização nas configurações do GitHub."
                        }) { Text("Desconectar") }
                    }
                }
            }
            if (isLoadingRepos) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("  Carregando repositórios...")
                }
            } else {
                Text("Seus repositórios públicos", fontWeight = FontWeight.SemiBold)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(repositories, key = { it.fullName }) { repo ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedRepo?.fullName == repo.fullName) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(14.dp),
                            onClick = { selectedRepo = repo }
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(repo.fullName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    repo.description.ifBlank { "Sem descrição" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    "${if (repo.isPrivate) "Privado" else "Público"} • branch: ${repo.defaultBranch}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
            selectedRepo?.let { repo ->
                Button(
                    onClick = {
                        isLoadingReadme = true
                        scope.launch {
                            try {
                                val readme = GithubApi.readReadme(token!!, repo.fullName)
                                val contextText = "REPOSITÓRIO GITHUB: ${repo.fullName}\nBranch padrão: ${repo.defaultBranch}\nREADME.md:\n$readme"
                                onContextReady(contextText)
                                status = "README de ${repo.fullName} enviado ao contexto do chat."
                            } catch (error: Exception) {
                                status = error.message ?: "Não foi possível importar o README."
                            } finally {
                                isLoadingReadme = false
                            }
                        }
                    },
                    enabled = !isLoadingReadme,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoadingReadme) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Importar README para o chat")
                }
            }
        }
        Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
}
