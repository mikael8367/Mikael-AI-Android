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
    val description: String
)

object ModelCatalog {
    val models = listOf(
        CatalogModel(
            id = "mikael-base", name = "Mikael AI", family = "Modelo autoral",
            engine = ModelEngine.MKAI, fileName = null, downloadUrl = null,
            approximateBytes = 0L, license = "Treinamento próprio",
            sourceUrl = "https://github.com/mikael8367/Mikael-AI-Android",
            description = "Motor próprio MKAI. Aguardando corpus licenciado e pesos treinados; ainda não há um modelo pronto para instalar."
        ),
        CatalogModel(
            id = "mikael-assistant", name = "Mikael AI Assistente", family = "Mikael AI • base Qwen 2.5 0.5B • Q4_K_M",
            engine = ModelEngine.LLAMA_CPP, fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            approximateBytes = 430_000_000L, license = "Apache-2.0 (modelo-base)",
            sourceUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF",
            description = "Assistente Mikael AI com instruções de programação e execução local. Usa Qwen 2.5 0.5B como modelo-base; não é um checkpoint treinado do zero."
        ),
        CatalogModel(
            id = "qwen-0.5b", name = "Qwen 2.5 0.5B Instruct", family = "GGUF • Q4_K_M",
            engine = ModelEngine.LLAMA_CPP, fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            approximateBytes = 430_000_000L, license = "Apache-2.0",
            sourceUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF",
            description = "Modelo pequeno para instruções. Download aproximado; requer runtime GGUF para conversar."
        ),
        CatalogModel(
            id = "smollm2-360m", name = "SmolLM2 360M Instruct", family = "GGUF • Q4_K_M",
            engine = ModelEngine.LLAMA_CPP, fileName = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf",
            approximateBytes = 290_000_000L, license = "Apache-2.0",
            sourceUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF",
            description = "Modelo compacto. Download aproximado; requer runtime GGUF para conversar."
        ),
        CatalogModel(
            id = "tinyllama-1.1b", name = "TinyLlama 1.1B Chat", family = "GGUF • Q4_K_M",
            engine = ModelEngine.LLAMA_CPP, fileName = "TinyLlama-1.1B-Chat-v1.0-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/tensorblock/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/TinyLlama-1.1B-Chat-v1.0-Q4_K_M.gguf",
            approximateBytes = 670_000_000L, license = "Apache-2.0",
            sourceUrl = "https://huggingface.co/tensorblock/TinyLlama-1.1B-Chat-v1.0-GGUF",
            description = "Modelo de 1,1B parâmetros. Download aproximado; requer runtime GGUF para conversar."
        )
    )
}
