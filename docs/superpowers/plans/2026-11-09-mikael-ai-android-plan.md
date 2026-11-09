# Plano de implementação — Mikael AI Android híbrida

## Objetivo
Manter uma aplicação Android nativa com a mesma navegação principal do Mikael AI original e oferecer dois modos claramente separados:
- **Offline:** inferência local com llama.cpp e GGUF, sem chamadas de chat ao servidor.
- **Online:** autenticação com APP_PASSWORD e uso da API já existente do Mikael AI hospedado.

O aplicativo não usa WebView. A chave LLM_API_KEY permanece no servidor e nunca é empacotada no APK.

## Etapa A — Base nativa
- [x] Repositório Android separado.
- [x] Gradle, Kotlin, Compose, NDK e CMake.
- [x] llama.cpp fixado na tag b12143.
- [x] JNI para carregar, gerar, cancelar e descarregar modelos.
- [x] Orçamento de memória e testes unitários iniciais.
- [x] Interface de cinco abas inferiores: Início, Conversas, Projetos, Arquivos e Ajustes.
- [x] Importação GGUF por Storage Access Framework para armazenamento privado.
- [x] Histórico offline local.

## Etapa B — Modo online e segurança
- [x] Cliente HTTP nativo HTTPS com cookie jar para sessão do servidor.
- [x] Login com APP_PASSWORD.
- [x] Salvar senha por padrão com AES-GCM e Android Keystore.
- [x] Reconexão automática somente quando o modo online está selecionado e há senha salva.
- [x] Chat online e histórico remoto.
- [x] Separar o histórico local do histórico online.
- [x] Tratar sessão expirada, erros de rede e cancelamento do cliente.
- [x] Upload e análise de arquivos/imagens e geração de arquivos.
- [x] Consulta de conectores do servidor.
- [ ] Validar o fluxo online contra o serviço real com conta de teste.

## Etapa C — Catálogo de modelos
- [x] Seleção dinâmica a partir de GET /api/models.
- [x] Branch complementar no repositório Mikael-AI com presets OpenRouter adicionais.
- [ ] Integrar e publicar a branch do backend para disponibilizar os novos modelos no serviço online.
- [ ] Confirmar cada preset com uma chamada real do provedor.
- [ ] Exibir indisponibilidade e falhas de provedor sem perder a conversa.

## Etapa D — Verificação técnica
- [ ] Confirmar execução real do workflow Android APK.
- [ ] Corrigir erros Gradle/Kotlin/CMake/NDK encontrados pelo CI.
- [ ] Validar ABI arm64-v8a e bibliotecas empacotadas.
- [ ] Verificar inferência JNI em dispositivo Android ARM64.
- [ ] Testar troca online/offline durante navegação, histórico e uso de arquivos.
- [ ] Testar sessão expirada, senha incorreta, servidor indisponível e recuperação.
- [ ] Testar pressão de memória, cancelamento, troca de modelo e retorno do segundo plano.
- [ ] Revisar acessibilidade, rotação e telas pequenas.
- [ ] Fazer teste físico no Galaxy A12 ou Android ARM64 equivalente.

## Etapa E — Auditoria final
- [ ] Revisar JNI, UTF-8, limites de contexto, cancelamento e liberação de memória.
- [ ] Auditar persistência e criptografia da senha.
- [ ] Auditar todos os endpoints e confirmar que offline não dispara requisições.
- [ ] Executar testes, lint e build debug limpos.
- [ ] Documentar limitações e só então declarar uma versão pronta.

## Restrições permanentes
- Não criar APK assinado de publicação sem pedido explícito.
- Não alterar a interface web nem o serviço Render sem autorização específica; a extensão de catálogo fica isolada em branch até integração.
- Não incluir chaves de provedor no aplicativo.
- Não anunciar recursos que ainda não foram implementados e validados.
