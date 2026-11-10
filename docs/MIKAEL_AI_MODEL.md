# Mikael AI Base — modelo autoral

## Objetivo

Treinar um modelo de linguagem próprio, especializado em programação e português brasileiro, para inferência local em Android ARM64.

## Regras de origem

- Não usar pesos pré-treinados, fine-tuning, destilação ou mesclagem de Qwen, Llama, SmolLM ou qualquer outro modelo.
- Tokenizer, arquitetura/configuração, pesos e pipeline de treinamento devem ser próprios.
- Dados precisam ter licença e proveniência registradas.
- O modelo só pode ser chamado de treinado do zero quando todos os pesos tiverem sido inicializados e treinados sem checkpoint externo.

## Estado atual

- Existe uma primeira implementação C++ própria de inferência Transformer no Android.
- O motor carrega somente o formato MKAI v1; não depende de llama.cpp nem aceita GGUF.
- O formato está descrito em [MKAI_FORMAT_V1.md](MKAI_FORMAT_V1.md).
- Ainda não há pesos treinados nem um arquivo MKAI oficial. Logo, ainda não existe um assistente funcional com qualidade de linguagem.

## Arquitetura de inferência inicial

- Transformer decoder-only com atenção causal multi-head.
- RMSNorm, projeções Q/K/V/O, conexões residuais e bloco SwiGLU.
- Embeddings de token e posição.
- Tokenizer byte-level provisório com 256 IDs.
- Pesos float32 no formato MKAI v1.

Esta é uma base experimental. Ainda faltam RoPE, cache KV, quantização, tokenizer treinável subword, amostragem avançada e otimizações específicas para ARM64.

## Pipeline de treinamento que falta

1. Definir configuração pequena compatível com memória real de Android.
2. Construir corpus legalmente utilizável com origem, licença e deduplicação registradas.
3. Criar tokenizer próprio e testes de cobertura para português e código.
4. Inicializar todos os pesos aleatoriamente e treinar o modelo do zero.
5. Executar pré-treinamento e acompanhar loss em conjuntos separados de treino, validação e teste.
6. Fazer treinamento supervisionado com exemplos próprios de programação e instruções.
7. Avaliar qualidade, segurança, alucinações e geração de código.
8. Exportar os pesos para MKAI, validar checksum e testar no Android ARM64.
9. Só então marcar Mikael AI Base como disponível.

## Hardware

O celular será o alvo de inferência, não o ambiente principal de treinamento. Treinar um modelo de linguagem do zero requer infraestrutura computacional separada. Isso não impede que o modelo final rode sem internet no Android.
