# Mikael AI Android — aplicativo nativo offline

Data: 2026-11-09

## Objetivo
Criar um aplicativo Android nativo da Mikael AI que execute conversas com um modelo GGUF diretamente no telefone. O aplicativo é exclusivamente offline para chat e inferência: não carrega o site, não usa WebView, não chama Render, OpenRouter ou qualquer endpoint remoto de IA.

## Critérios de aceitação
1. Interface nativa em Kotlin/Jetpack Compose, organizada em abas inferiores animadas: Chat, Modelos e Configurações.
2. Inferência local por llama.cpp via JNI/NDK, sem servidor HTTP local ou remoto.
3. Modelos GGUF importados uma vez para o armazenamento privado do aplicativo; o usuário pode selecionar, descarregar e excluir modelos.
4. Histórico de conversas persistido localmente no aparelho.
5. Orçamento de memória configurável, com estimativa conservadora de modelo, contexto e buffers. A interface deve explicar que é um orçamento do runtime, não um limite absoluto de todo o processo Android.
6. Rejeitar arquivos inválidos, contexto fora dos limites e modelos que excedam o orçamento estimado antes de chamar o motor nativo.
7. Cancelamento de geração e liberação idempotente do modelo/contexto.
8. O aplicativo não contém WebView, modo Cloud, URLs de chat, clientes OpenRouter ou dependência do Render.
9. Primeira entrega é texto offline. Visão, análise multimodal, geração de imagens, pesquisa web e ferramentas externas ficam fora de escopo até terem implementação e testes locais.
10. Workflow CI executa testes, lint e build debug ARM64 e publica o APK como artefato.

## Abordagens consideradas
- llama.cpp integrado diretamente por JNI/NDK: escolhido por não exigir servidor extra e permitir controle do ciclo de vida e da memória.
- Servidor local separado: rejeitado por adicionar processo, portas e overhead desnecessário em aparelhos com pouca RAM.
- WebView/Cloud: rejeitado porque viola o requisito de funcionamento exclusivamente offline.

## Decisões
- Kotlin + Jetpack Compose para a interface.
- llama.cpp fixado em uma tag imutável e compilado para ARM64 com opções portáveis.
- O primeiro alvo de aparelho é um Android ARM64 de entrada; começar com modelos pequenos e quantizados, sem prometer que modelos grandes caberão.
- O modelo é selecionado/importado pelo seletor de documentos do Android; nenhuma fonte de modelo é embutida sem validação de licença, tamanho e checksum.
- Não adicionar permissão INTERNET enquanto o app não tiver uma funcionalidade explícita de download próprio. A importação de arquivos baixados pelo usuário funciona pelo seletor de documentos.
- Conversas, preferências e modelos permanecem no armazenamento privado do app.
- O site, backend e serviço Render do projeto original permanecem intocados.

## Riscos e validação
- Verificar compatibilidade real da API C do llama.cpp fixado.
- Verificar build Gradle + NDK em CI; não declarar sucesso sem logs reais.
- Auditar concorrência entre geração, cancelamento, descarregamento e troca de modelo.
- Verificar limites, overflow, arquivos truncados, espaço insuficiente e recuperação de falhas.
- Revisar textos/labels para remover referências acidentais a Cloud, Render, OpenRouter, WebView e APIs remotas.
- Testar em aparelho ARM64 real antes de declarar compatibilidade prática.
