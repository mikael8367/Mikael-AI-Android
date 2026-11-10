# Catálogo de quatro modelos — Mikael AI Android

Data: 2026-11-10

## Objetivo
Disponibilizar quatro modelos na aba Modelos do app Android: o Mikael AI autoral em MKAI e três modelos GGUF independentes (Qwen2.5-0.5B-Instruct, SmolLM2-360M-Instruct e TinyLlama-1.1B-Chat). O usuário pode baixar, importar, selecionar, carregar, descarregar e excluir modelos compatíveis.

## Abordagens
- **Dois motores separados (escolhida):** preservar o motor nativo autoral MKAI para Mikael AI e integrar llama.cpp fixado por tag/commit para GGUF. Permite suportar arquiteturas GGUF reais sem converter ou misturar pesos.
- Reescrever o motor próprio para GGUF: rejeitada nesta entrega; alto risco e custo para implementar parser, quantização e arquiteturas.
- Falsos botões de catálogo sem runtime: rejeitada porque anuncia modelos que não podem executar.

## Requisitos
1. Manter o motor MKAI e o pipeline de treinamento do Mikael AI sem alterações semânticas.
2. Integrar llama.cpp por JNI/NDK, compilado para ABI arm64-v8a, com build pinado e CPU-only.
3. Usar modelos Instruct GGUF com licença e fonte identificadas; mostrar tamanho aproximado e aviso de RAM.
4. Download retomável/progresso quando viável, gravação temporária, validação de GGUF e renomeação atômica; falhas removem o parcial.
5. Nunca considerar um modelo instalado antes da validação.
6. Persistir modelo ativo e engine correspondente; impedir que um arquivo de um motor seja passado ao outro.
7. Cancelar geração antes de trocar/descarregar motores; liberar JNI com segurança.
8. Manter abas inferiores animadas e a navegação existente.
9. Testes unitários para catálogo, seleção de motor, validação de arquivos, progresso/estado de download e limites de memória.
10. CI executa build nativo, testes, lint e APK debug; APK somente como artefato CI, sem publicar/mesclar main.

## Catálogo inicial
- Mikael AI Base — MKAI; modelo autoral, disponibilidade condicionada à existência de pesos treinados.
- Qwen2.5-0.5B-Instruct — GGUF Q4_K_M.
- SmolLM2-360M-Instruct — GGUF Q4_K_M.
- TinyLlama-1.1B-Chat-v1.0 — GGUF Q4_K_M.

As entradas GGUF devem usar URLs HTTPS oficiais/de repositórios de quantização conhecidos, identificar a licença do modelo original e do repositório de quantização e validar o arquivo baixado. Não prometer que todos os modelos terão desempenho adequado em todo aparelho.

## Restrições e não objetivos
- O Mikael AI não recebe pesos pré-treinados, fine-tuning ou destilação de modelos de terceiros.
- Não treinar o modelo no telefone; o treino autoral continua no pipeline dedicado.
- Sem servidor de inferência remoto, WebView ou API de chat.
- Não gerar APK de release assinado.
- Não alterar main.
- Não prometer qualidade do Mikael AI enquanto não houver corpus e pesos próprios treinados.

## Critérios de aceite
- O catálogo apresenta exatamente os quatro modelos, com estado honesto: não instalado, baixando, instalado, ativo, erro ou aguardando treino.
- Os três GGUF são reconhecidos pelo runtime llama.cpp; o MKAI continua no runtime autoral.
- Importações/downloads truncados, formato errado, falta de espaço, troca durante geração e cancelamento não deixam o app em estado inconsistente.
- Testes, lint e build ARM64 passam em CI. Teste físico no Galaxy A12 ainda deve ser informado separadamente.
