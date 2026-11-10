# Treinamento do zero da Mikael AI

Este pipeline treina um Transformer pequeno com pesos inicializados aleatoriamente e exporta o formato MKAI v1 consumido pelo motor C++ do Android. Não baixa checkpoints, pesos, tokenizer ou modelos de terceiros.

## O que já está implementado

- Arquitetura PyTorch própria alinhada camada por camada ao forward pass C++.
- Inicialização aleatória de todos os pesos.
- Treinamento causal por previsão do próximo byte UTF-8.
- Separação de treino e validação, loss, clipping de gradiente e AdamW.
- Exportação float32 little-endian na ordem exata esperada pelo carregador MKAI v1.
- Validação de cabeçalho, dimensões, tamanho do arquivo e valores finitos.
- Workflow GitHub Actions manual, executado em CPU, que publica o arquivo .mkai como artefato. Não compila nem publica APK.

## O que ainda é necessário antes de obter um modelo útil

**O projeto ainda não inclui um corpus de treinamento.** Não é correto inventar um modelo pronto ou treinar com meia dúzia de frases e chamá-lo de assistente inteligente.

O repositório agora contém training/corpus.txt, um corpus inicial autoral com exemplos simples de português e programação. Ele é apenas um conjunto didático pequeno para validar o primeiro ciclo de treinamento, não um conjunto de dados suficiente para um assistente avançado. Amplie-o com exemplos originais, revisados e legalmente utilizáveis. Nunca inclua dados pessoais, senhas ou segredos.

Para ensinar formato de conversa, organize cada exemplo com separadores textuais consistentes, por exemplo:

    <|im_start|>user
    Explique o que é uma variável em Kotlin.
    <|im_end|>
    <|im_start|>assistant
    Uma variável é um nome associado a um valor...
    <|im_end|>

O motor MKAI v1 prevê bytes, não possui tokens especiais de fim de resposta e ainda não integra o BPE MKTOK. Por isso, o corpus e a avaliação precisam considerar essa limitação. O modelo aprende padrões de bytes e texto. Com este corpus pequeno, espere respostas simples e experimentais; não espere conhecimento geral ou programação avançada.

## Como treinar usando somente o celular

1. No GitHub, abra o repositório e selecione a branch fix/ai-runtime-stability.
2. Crie training/corpus.txt e adicione seu corpus UTF-8 licenciado. O arquivo precisa ter pelo menos 4 KiB e não pode ultrapassar 100 MiB.
3. Abra a aba Actions do repositório.
4. Escolha Treinar modelo próprio Mikael AI e toque em Run workflow.
5. Informe o caminho do corpus e mantenha inicialmente 2500 passos e contexto 512.
6. Quando terminar, baixe o artefato mikael-ai-original-mkai-v1.
7. Importe o arquivo .mkai pela aba Modelos do aplicativo.

O treinamento inicial ocorre nos runners de CPU do GitHub Actions, não no Galaxy A12. O arquivo .mkai exportado deve ser baixado e importado no app; depois disso, a inferência acontece localmente no Android. O app não treina os pesos no telefone nesta etapa.

## Configuração inicial

- Dimensão: 64
- Camadas: 2
- Cabeças: 4
- Feed-forward: 128
- Contexto padrão do workflow: 512 bytes/tokens byte-level
- Batch: 4
- Passos: 1200
- Vocabulário MKAI v1: 256 bytes UTF-8

Esses parâmetros são um ponto de partida experimental, não uma promessa de qualidade ou velocidade. A inferência atual recalcula a sequência inteira a cada token e ainda precisa de cache KV e benchmarks no aparelho.

## Compatibilidade

O exportador usa exatamente o layout descrito em docs/MKAI_FORMAT_V1.md. O MKTOK BPE segue separado porque o formato v1 aceita somente 256 IDs. A migração para MKAI v2 com BPE integrado continua pendente.