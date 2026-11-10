# Formato MKAI 0001 — motor próprio

Formato exclusivo do motor nativo Mikael AI. Não é GGUF e não pode ser aberto por llama.cpp.

## Cabeçalho binário

Todos os campos são little-endian. O arquivo começa com 8 bytes ASCII MKAI0001 seguidos por oito inteiros sem sinal de 32 bits:

| Índice | Campo | Regra |
|---:|---|---|
| 0 | version | 1 |
| 1 | dim | 16 a 1024 |
| 2 | layers | 1 a 16 |
| 3 | heads | 1 a 32 e divisor de dim |
| 4 | ffn | dim a 4096 |
| 5 | vocab | exatamente 256 |
| 6 | max_seq | 16 a 8192 |
| 7 | reserved | zero na versão 1 |

O cabeçalho ocupa 40 bytes. Em seguida, os tensores são armazenados na ordem abaixo, como float32 little-endian, sem padding:

1. token_embedding[vocab, dim]
2. position_embedding[max_seq, dim]
3. Para cada camada:
   - attention_norm[dim]
   - query[dim, dim]
   - key[dim, dim]
   - value[dim, dim]
   - attention_output[dim, dim]
   - ffn_norm[dim]
   - ffn_gate[ffn, dim]
   - ffn_up[ffn, dim]
   - ffn_down[dim, ffn]
4. final_norm[dim]
5. lm_head[vocab, dim]

As matrizes são row-major. O MKAI v1 permanece compatível somente com o fallback byte-level UTF-8 (IDs 0–255). O tokenizer BPE autoral MKTOK v1 foi implementado separadamente; ele pode produzir IDs acima de 255 e, por isso, ainda não pode ser conectado a pesos MKAI v1. A integração exige MKAI v2 com dimensão de vocabulário sincronizada com o arquivo MKTOK.

## Implementação atual

O motor C++ implementa embedding, RMSNorm, projeções lineares, atenção causal multi-head, conexão residual, SwiGLU e projeção de logits. A inferência ocorre no processo Android, em CPU, sem chamadas a serviços de IA.

O carregador verifica magic, versão, dimensões, limites de alocação por tensor, quantidade de pesos, valores finitos e ausência de bytes extras. O app só importa arquivos .mkai.

## Limitações conhecidas desta etapa

- Ainda não há pesos MKAI treinados nem arquivo .mkai oficial para instalar.
- O modelo não produz respostas úteis até que os pesos sejam treinados com o pipeline próprio.
- A implementação atual recalcula a sequência inteira a cada token; é um protótipo e pode ficar lenta em celulares.
- A inferência MKAI v1 ainda não consome MKTOK BPE; o módulo BPE está implementado separadamente e aguarda MKAI v2.
- A saída é greedy e o contexto usa embeddings posicionais aprendidos.
- O arquivo usa float32; o tamanho pode ser alto. A validação de memória do app deve ser aplicada antes do carregamento.

## Próximas etapas

1. Criar testes nativos para validar cabeçalho, dimensões, forward pass e cancelamento.
2. Criar MKAI v2 integrado ao tokenizer MKTOK, com exportador e leitor de pesos testados.
3. Treinar o Transformer do zero usando exatamente o vocabulário MKTOK.
4. Treinar, avaliar e exportar os primeiros pesos próprios.
5. Otimizar a inferência com cache KV, quantização e benchmark ARM64.