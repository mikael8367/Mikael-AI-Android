# Mikael AI Base — especificação do modelo autoral

## Objetivo

Criar um modelo de linguagem chamado **Mikael AI Base**, especializado em programação e preparado para inferência local em Android ARM64.

Este projeto deve produzir pesos próprios. Não pode ser apresentado como "feito do zero" se estiver apenas renomeando, mesclando ou fazendo fine-tuning de pesos de Qwen, SmolLM ou qualquer outro modelo pré-treinado.

## Estado atual

- A tela Android já reserva um cartão para o Mikael AI Base.
- O cartão informa corretamente que ainda não existe um arquivo de pesos pronto para instalar.
- O catálogo externo de GGUF é separado do projeto de pesos autorais.
- Nenhum peso de terceiros será copiado para dentro do Mikael AI Base.

## Proposta técnica inicial

- Arquitetura: Transformer decoder-only, com implementação e configuração documentadas pelo projeto.
- Inicialização: pesos inicializados aleatoriamente; nenhum checkpoint pré-treinado.
- Tokenizer: tokenizer treinado para o corpus próprio, com cobertura de português brasileiro, identificadores e sintaxe de linguagens de programação.
- Alvo inicial de pesquisa: faixa de 100M–500M parâmetros. A configuração final depende dos testes de qualidade, custo de treinamento e memória do Android.
- Exportação: GGUF compatível com a versão de llama.cpp incorporada no aplicativo, após validação do tokenizer e do template de conversa.
- Quantização inicial pretendida: Q4_K_M, comparando qualidade e memória com versões menos quantizadas.

## Etapas necessárias

1. Definir e documentar a arquitetura e a configuração do tokenizer.
2. Montar um corpus legalmente utilizável, com fontes, licenças e proveniência registradas.
3. Deduplicar, filtrar e separar os dados em treino, validação e teste.
4. Treinar o tokenizer e inicializar todos os pesos do modelo do zero.
5. Fazer pré-treinamento e medir perda/qualidade em conjuntos de validação.
6. Fazer treinamento supervisionado para instruções de programação em português, mantendo a proveniência dos dados.
7. Avaliar geração de código, explicações, matemática básica, português e alucinações.
8. Converter para GGUF, quantizar e testar a inferência no Android ARM64.
9. Publicar o arquivo de pesos, checksum SHA-256, licença e relatório de avaliação.
10. Só então habilitar o botão **Baixar e instalar Mikael AI Base** no catálogo.

## Limite de hardware

O Galaxy A12 pode executar modelos pequenos quantizados, mas não é uma plataforma adequada para treinar um LLM desse porte do zero. O treinamento precisará de infraestrutura de computação separada; o telefone será usado para baixar, carregar e avaliar o modelo já treinado.

## Critério para declarar o modelo pronto

O modelo só muda de "Em desenvolvimento" para "Disponível" quando houver:
- pesos realmente treinados do zero;
- arquivo GGUF íntegro e compatível;
- checksum publicado e conferido;
- licença e origem dos dados documentadas;
- teste de carregamento e geração no motor nativo;
- relatório de qualidade e limitações.

Até cumprir esses critérios, o aplicativo não deve simular um download nem marcar o modelo como instalado.
