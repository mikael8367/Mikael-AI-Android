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
- Tokenizer BPE byte-level autoral MKTOK v1 implementado e testado; o modelo MKAI v1 ainda usa apenas fallback de 256 IDs até a migração para MKAI v2.
- Pesos float32 no formato MKAI v1.

Esta é uma base experimental. Ainda faltam integrar MKTOK ao formato MKAI v2, treinar o vocabulário com um corpus licenciado e treinar os pesos com esse mesmo vocabulário, além de RoPE, cache KV, quantização, amostragem avançada e otimizações específicas para ARM64.

## Pipeline de treinamento que falta

1. Definir configuração pequena compatível com memória real de Android.
2. Construir corpus legalmente utilizável com origem, licença e deduplicação registradas.
3. Treinar o tokenizer BPE MKTOK com corpus próprio e licenciado, com testes de cobertura para português e código.
4. Migrar os pesos para MKAI v2, dimensionando embeddings e LM head pelo vocabulário MKTOK.
5. Inicializar todos os pesos aleatoriamente e treinar o modelo do zero.
6. Executar pré-treinamento e acompanhar loss em conjuntos separados de treino, validação e teste.
7. Fazer treinamento supervisionado com exemplos próprios de programação e instruções.
8. Avaliar qualidade, segurança, alucinações e geração de código.
9. Exportar os pesos para MKAI, validar checksum e testar no Android ARM64.
10. Só então marcar Mikael AI Base como disponível.

## Hardware

O celular será o alvo de inferência, não o ambiente principal de treinamento. Treinar um modelo de linguagem do zero requer infraestrutura computacional separada. Isso não impede que o modelo final rode sem internet no Android.
