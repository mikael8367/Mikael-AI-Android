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
            id = "mikael-base", name = "Mikael AI", family = "Modelo autoral • MKAI v1",
            engine = ModelEngine.MKAI, fileName = "mikael-base.mkai",
            downloadUrl = "https://github.com/mikael8367/Mikael-AI-Android/releases/download/mikael-ai-model-v1/mikael-base.mkai",
            approximateBytes = 591_144L, license = "Licença não declarada",
            sourceUrl = "https://github.com/mikael8367/Mikael-AI-Android/actions/workflows/train-mikael-model.yml",
            description = "Checkpoint autoral MKAI v1 já publicado. Baixe e instale diretamente no aplicativo; versões futuras podem ser treinadas com corpus próprio."
        ),
        CatalogModel(
            id = "qwen-0.5b", name = "Qwen2.5-Coder 0.5B Instruct", family = "GGUF • Q4_K_M",
            engine = ModelEngine.LLAMA_CPP, fileName = "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            approximateBytes = 491_000_000L, license = "Apache-2.0",
            sourceUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF",
            description = "Modelo especializado em geração, raciocínio e correção de código; 0,49B parâmetros. Q4_K_M requer cerca de 491 MB. A documentação marca foco em inglês; qualidade de instruções em português e velocidade ainda precisam ser medidas no celular."
        ),
        CatalogModel(
            id = "smollm2-360m", name = "SmolLM2 360M Instruct", family = "GGUF • Q4_K_M",
            engine = ModelEngine.LLAMA_CPP, fileName = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf",
            approximateBytes = 270_000_000L, license = "Apache-2.0",
            sourceUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF",
            description = "Modelo compacto de 360M parâmetros. O cartão do modelo informa foco principal em inglês; pode ser mais leve, mas não é a primeira escolha para conversar em português."
        ),
        CatalogModel(
            id = "tinyllama-1.1b", name = "TinyLlama 1.1B Chat", family = "GGUF • Q4_K_M",
            engine = ModelEngine.LLAMA_CPP, fileName = "TinyLlama-1.1B-Chat-v1.0-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/tensorblock/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/TinyLlama-1.1B-Chat-v1.0-Q4_K_M.gguf",
            approximateBytes = 622_000_000L, license = "Apache-2.0",
            sourceUrl = "https://huggingface.co/tensorblock/TinyLlama-1.1B-Chat-v1.0-GGUF",
            description = "Modelo de 1,1B parâmetros, maior consumo de RAM. Não recomendado como primeira opção no Galaxy A12 sem teste de memória."
        )
    )
}
