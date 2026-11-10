# MKTOK 0001 — tokenizer BPE autoral

O tokenizer MKTOK foi implementado em Kotlin puro, sem bibliotecas de tokenização externas, SentencePiece, Hugging Face Tokenizers ou vocabulários pré-treinados.

## Algoritmo

- Tokenização byte-level sobre UTF-8.
- IDs 0–255 representam cada byte.
- Cada merge BPE cria um novo ID, começando em 256.
- O treinamento conta pares adjacentes no corpus e aplica o par mais frequente.
- Empates são resolvidos pelo menor par numérico (left, right), para resultados determinísticos.
- A codificação aplica os merges na mesma ordem em que foram aprendidos.
- A decodificação concatena os bytes de cada token e reconstrói o texto UTF-8.

## Formato do arquivo

O arquivo .mktok usa little-endian:

| Offset | Campo | Tipo |
|---:|---|---|
| 0 | Magic MKTOK001 | 8 bytes |
| 8 | Version = 1 | uint32 |
| 12 | Base vocabulary = 256 | uint32 |
| 16 | Vocabulary size | uint32 |
| 20 | Merge count | uint32 |
| 24 | Lista de merges | pares uint32 (left, right) |
| final - 32 | SHA-256 de todos os bytes anteriores | 32 bytes |

O ID de cada merge é 256 + índice. Cada merge só pode referenciar IDs anteriores ao seu próprio ID. O tamanho do vocabulário precisa ser 256 + merge_count.

## Limites de segurança

- Corpus de treinamento limitado a 16 MiB por execução.
- Vocabulário limitado a 4.352 IDs (256 bytes-base + 4.096 merges).
- Armazenamento expandido dos tokens limitado a 16 MiB.
- Arquivo limitado ao tamanho máximo definido pelo número de merges.
- SHA-256 obrigatório na leitura.
- Validação de magic, versão, dimensões, referências, tamanho e IDs na decodificação.
- Escrita em arquivo temporário, com substituição atômica quando suportada.

## Uso no código

MikaelTokenizer.train(corpus, targetVocabularySize) treina um vocabulário do zero.
encode(text) transforma texto em IDs e decode(ids) reconstrói o texto.
save(file) e MikaelTokenizer.load(file) persistem e validam o arquivo .mktok.

## Estado de integração

O tokenizer já tem implementação e testes unitários, mas ainda não está conectado à inferência Transformer. O formato MKAI v1 continua fixado em 256 IDs e usa bytes diretamente. A integração exige uma versão nova do formato de pesos, com dimensão de vocabulário igual à do MKTOK, e exportação de pesos treinados com exatamente o mesmo tokenizer. Não se deve combinar um MKTOK treinado com pesos de outro vocabulário.