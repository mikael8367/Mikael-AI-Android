# Plano de implementação — Mikael AI Android

## Etapa A — Base nativa
- [x] Criar repositório Android separado e documentação offline-only.
- [x] Configurar Gradle, Kotlin, Compose, NDK e CMake.
- [x] Fixar llama.cpp na tag b12143.
- [x] Criar contrato JNI de carregar, gerar, cancelar e descarregar.
- [x] Criar orçamento de memória e testes unitários iniciais.
- [x] Criar interface nativa com abas inferiores Chat, Modelos e Configurações.
- [x] Importar GGUF por Storage Access Framework para armazenamento privado.
- [x] Persistir histórico local básico.

## Etapa B — Verificação de compilação e correções
- [ ] Confirmar execução real do workflow Android APK.
- [ ] Corrigir erros Gradle/Kotlin/CMake/NDK encontrados pelo CI.
- [ ] Validar ABI arm64-v8a e bibliotecas empacotadas.
- [ ] Verificar execução JNI em dispositivo Android ARM64.
- [ ] Validar geração com um modelo GGUF compatível com o formato de prompt ChatML.

## Etapa C — Robustez do aplicativo
- [ ] Adicionar seleção de múltiplas conversas locais.
- [ ] Adicionar streaming real de tokens para a interface.
- [ ] Adicionar catálogo e download retomável de modelos, com fonte, licença e SHA-256 verificados.
- [ ] Melhorar detecção de espaço insuficiente e recuperação de importação interrompida.
- [ ] Testar pressão de memória, cancelamento, troca de modelo e retorno do app do segundo plano.
- [ ] Revisar acessibilidade, rotação e telas pequenas.

## Etapa D — Auditoria final
- [ ] Fazer auditoria textual de todo o repositório para garantir ausência de Cloud, WebView, Render, OpenRouter e endpoints de chat.
- [ ] Revisar JNI, UTF-8, limites de contexto, cancelamento e liberação de memória.
- [ ] Executar testes, lint e build debug limpos.
- [ ] Fazer teste físico no Galaxy A12 ou outro Android ARM64 equivalente.
- [ ] Documentar limitações e só então declarar uma versão pronta.

## Restrições permanentes
- Não criar APK assinado de publicação sem pedido explícito.
- Não alterar o site, backend ou serviço Render do repositório Mikael-AI original.
- Não adicionar chat remoto nem modo Cloud.
- Não anunciar visão, geração de imagens, pesquisa web ou ferramentas como disponíveis até implementação e validação locais.
