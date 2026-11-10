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

/** Catálogo local. Pesos são baixados somente após confirmação explícita do usuário. */
object ModelCatalog {
    const val TEAM_MODEL_ID = "mikael-ai-4"

    val models = listOf(
        CatalogModel(
            id = "mikael-base",
            name = "Mikael AI (modelo autoral)",
            family = "Modelo próprio • MKAI v1 • CPU local",
            engine = ModelEngine.MKAI,
            fileName = "mikael-base.mkai",
            downloadUrl = "https://github.com/mikael8367/Mikael-AI-Android/releases/download/mikael-ai-model-v1/mikael-base.mkai",
            approximateBytes = 591_144L,
            license = "Licença não declarada",
            sourceUrl = "https://github.com/mikael8367/Mikael-AI-Android/releases/tag/mikael-ai-model-v1",
            description = "Checkpoint autoral experimental (~0,6 MB), contexto máximo de 512 bytes no runtime atual. Não equivale em capacidade aos modelos Qwen, SmolLM2 ou TinyLlama; mensagens longas podem exceder o contexto.",
            expectedSha256 = "86e194c24832621af0786eb1e429772bdf29a8ccccdcb5446c84d771682ca0ee"
        ),
        CatalogModel(
            id = "qwen-0.5b",
            name = "Qwen2.5-Coder 0.5B Instruct",
            family = "Modelo de programação • GGUF • Q4_K_M",
            engine = ModelEngine.LLAMA_CPP,
            fileName = "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            approximateBytes = 491_000_000L,
            license = "Apache-2.0",
            sourceUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF",
            description = "Modelo compacto especializado em código. Formato GGUF Q4_K_M; inferência local via llama.cpp.",
            expectedSha256 = "1d9614638d18024d0fbb36575a15f1302a3adf044df10345688ec4f6e1c4ff32"
        ),
        CatalogModel(
            id = "smollm2-360m",
            name = "SmolLM2 360M Instruct",
            family = "Modelo compacto • GGUF • Q4_K_M",
            engine = ModelEngine.LLAMA_CPP,
            fileName = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/tensorblock/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf",
            approximateBytes = 271_000_000L,
            license = "Apache-2.0",
            sourceUrl = "https://huggingface.co/tensorblock/SmolLM2-360M-Instruct-GGUF",
            description = "Modelo de instruções de 360 milhões de parâmetros em GGUF Q4_K_M, compatível com a família de runtime llama.cpp."
        ),
        CatalogModel(
            id = "tinyllama-1.1b",
            name = "TinyLlama 1.1B Chat",
            family = "Modelo conversacional • GGUF • Q4_K_M",
            engine = ModelEngine.LLAMA_CPP,
            fileName = "TinyLlama-1.1B-Chat-v1.0-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/tensorblock/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/TinyLlama-1.1B-Chat-v1.0-Q4_K_M.gguf",
            approximateBytes = 622_000_000L,
            license = "Apache-2.0",
            sourceUrl = "https://huggingface.co/tensorblock/TinyLlama-1.1B-Chat-v1.0-GGUF",
            description = "Modelo conversacional de 1,1 bilhão de parâmetros. É o participante mais pesado; o app verifica a memória disponível antes de carregá-lo."
        )
    )

    val teamParticipants: List<TeamParticipant>
        get() = listOf(
            TeamParticipant("mikael-base", "Mikael AI"),
            TeamParticipant("qwen-0.5b", "Qwen 2.5 0.5B"),
            TeamParticipant("smollm2-360m", "SmolLM2 360M"),
            TeamParticipant("tinyllama-1.1b", "TinyLlama 1.1B")
        )

    val teamDownloadBytes: Long
        get() = models.sumOf { it.approximateBytes }
}
