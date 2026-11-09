package com.mikael.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

object GitHubTokenVault {
    private const val KEY_ALIAS = "mikael_ai_github_token_key"
    private const val PREFS = "github_secure"
    private const val TOKEN = "encrypted_token"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun save(context: Context, token: String) {
        require(token.isNotBlank() && token.length <= 512) { "Token inválido." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(TOKEN, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()
        require(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(TOKEN)) {
            "Não foi possível salvar o token protegido."
        }
    }

    fun read(context: Context): String? = runCatching {
        val packed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TOKEN, null) ?: return null
        val bytes = Base64.decode(packed, Base64.NO_WRAP)
        require(bytes.size > 12) { "Credencial salva está inválida." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN).commit()
    }
}

object GitHubConnector {
    fun validate(token: String): String {
        val connection = (URL("https://api.github.com/user").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val reason = when (code) {
                    401 -> "Token inválido ou expirado."
                    403 -> "GitHub recusou o acesso. Verifique permissões e limites."
                    else -> "GitHub respondeu HTTP $code."
                }
                error(reason)
            }
            val body = connection.inputStream.bufferedReader().use { it.readText().take(64 * 1024) }
            val login = org.json.JSONObject(body).optString("login")
            require(login.isNotBlank()) { "O GitHub não retornou o usuário." }
            return login
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
fun GitHubConnectorCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (GitHubTokenVault.read(context) != null) "Token protegido salvo neste aparelho." else "Não conectado.") }
    var busy by remember { mutableStateOf(false) }
    var repoRead by remember { mutableStateOf(true) }
    var contentsRead by remember { mutableStateOf(true) }
    var issuesRead by remember { mutableStateOf(false) }
    var pullRequestsRead by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151D19))) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Conector GitHub", style = MaterialTheme.typography.titleMedium)
            Text(
                "A conexão GitHub usa internet apenas quando você pedir. O chat e o modelo continuam locais.",
                color = Color(0xFF9EAAA2)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/login")))
                }) { Text("Entrar no GitHub") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new")))
                }) { Text("Criar token") }
            }
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Token pessoal do GitHub") },
                placeholder = { Text("github_pat_...") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Text("Permissões que o Mikael AI poderá usar (o token também precisa permitir):", color = Color(0xFF9EAAA2))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = repoRead, onClick = { repoRead = !repoRead }, label = { Text("Repositórios") })
                FilterChip(selected = contentsRead, onClick = { contentsRead = !contentsRead }, label = { Text("Código") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = issuesRead, onClick = { issuesRead = !issuesRead }, label = { Text("Issues") })
                FilterChip(selected = pullRequestsRead, onClick = { pullRequestsRead = !pullRequestsRead }, label = { Text("Pull requests") })
            }
            Text(
                "Essas opções são uma preferência local, não concedem permissões ao token. Configure os acessos na página do GitHub. Comece somente com leitura; não habilite escrita se não precisar.",
                color = Color(0xFFFFD18A),
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = {
                    val candidate = token.trim()
                    if (candidate.isBlank()) {
                        status = "Cole um token para validar."
                    } else {
                        busy = true
                        status = "Validando com api.github.com..."
                        scope.launch {
                            try {
                                val user = withContext(Dispatchers.IO) { GitHubConnector.validate(candidate) }
                                GitHubTokenVault.save(context, candidate)
                                username = user
                                token = ""
                                status = "Conectado como @$user. Token criptografado pelo Android Keystore."
                            } catch (error: Exception) {
                                status = error.message ?: "Falha ao conectar."
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                enabled = !busy && token.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Validando..." else "Validar e conectar") }
            if (username.isNotBlank()) Text("Conta: @$username", color = Color(0xFF8BE7B7))
            Text(status, color = Color(0xFF9EAAA2), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = {
                    GitHubTokenVault.clear(context)
                    username = ""
                    token = ""
                    status = "Token removido do armazenamento protegido."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Desconectar e apagar token") }
        }
    }
}
