package com.mikael.ai

enum class ModelEngine { MKAI, LLAMA_CPP }

data class CatalogModel(
    val id: String,
    val name: String,
    val family: String,
    val engine: ModelEngine,
    val fileName: String?,
    val downloadUrl: String?,
    val approximateBytes: Long,
    val license: String,
    val sourceUrl: String,
    val description: String,
    val expectedSha256: String? = null
)

/**
 * Mikael AI usa Qwen2.5-Coder como único modelo de conversa.
 * O nome e a identidade do aplicativo continuam sendo Mikael AI.
 */
object ModelCatalog {
    val models = listOf(
        CatalogModel(
            id = "qwen-0.5b",
            name = "Qwen2.5-Coder 0.5B Instruct",
            family = "Modelo principal • GGUF • Q4_K_M",
            engine = ModelEngine.LLAMA_CPP,
            fileName = "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            approximateBytes = 491_000_000L,
            license = "Apache-2.0",
            sourceUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF",
            description = "Modelo principal do Mikael AI para conversar, explicar e escrever código. Os pesos são de terceiros (Qwen), não um modelo treinado do zero. O arquivo Q4_K_M ocupa aproximadamente 491 MB; qualidade em português e desempenho precisam ser avaliados no aparelho.",
            expectedSha256 = "1d9614638d18024d0fbb36575a15f1302a3adf044df10345688ec4f6e1c4ff32"
        )
    )
}
