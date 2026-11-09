# Mikael AI Android

Aplicativo Android nativo com modo offline local e modo online conectado ao serviço Mikael AI.

## Interface
- Cinco abas inferiores animadas: Início, Conversas, Projetos, Arquivos e Ajustes.
- Tema escuro verde alinhado à identidade visual do Mikael AI original.
- Layout pensado para uso em celular e navegação por toque.

## Modo online
- Login com a senha de acesso APP_PASSWORD configurada no servidor Mikael AI.
- Opção para salvar a senha no aparelho; ela é criptografada com AES-GCM e uma chave mantida pelo Android Keystore.
- A sessão do servidor usa cookie HttpOnly em memória; ao reabrir o app, a senha salva é usada para autenticar novamente.
- A chave LLM_API_KEY do provedor permanece exclusivamente no servidor. Ela não deve ser colocada no APK.
- Conversas e histórico online são obtidos da API do servidor.
- A conexão requer internet e o serviço online disponível.

## Modo offline
- Inferência local com llama.cpp via JNI/NDK e modelos GGUF importados pelo seletor de arquivos do Android.
- O modo offline não envia mensagens ao servidor.
- A disponibilidade e velocidade dependem do tamanho/quantização do modelo, contexto, RAM e CPU do aparelho.

## Modelos online
A API original oferece Mikael AI, DeepSeek V4.1 Flash e GPT-4o mini. A branch complementar feature/android-model-catalog do repositório Mikael-AI adiciona presets para Gemini 2.5 Flash, Claude Sonnet 4.5, Qwen3 Coder Plus e GPT-5 mini. Esses modelos adicionais só aparecerão quando essa atualização do backend for integrada e publicada no serviço online. A disponibilidade depende da chave e dos modelos liberados no provedor.

## Segurança
- A senha é criptografada no aparelho pelo Android Keystore quando a opção Salvar senha está ativada.
- O app não armazena a chave do provedor de IA.
- O tráfego online usa HTTPS.
- O modo offline permanece sem chamadas de chat remotas.

## Compilação
O workflow .github/workflows/android-apk.yml executa testes unitários, lint e assembleDebug para main e feature/native-online-hybrid-ui. O APK debug é publicado como artefato do GitHub Actions quando a compilação termina com sucesso.

## Estado desta versão
A interface nativa de cinco abas, login online, persistência protegida da senha, chat online/offline, upload/análise de arquivos, geração de arquivos e consulta de conectores estão implementados nesta branch. A aba Projetos lista conectores e encaminha a administração do GitHub/MCP para a interface web original; ainda não reproduz todas as ações de gerenciamento do site. A geração de arquivos e análise de imagens usam o backend remoto. A compilação e os testes em aparelho precisam ser confirmados antes de considerar a versão pronta.


Aplicativo Android nativo da Mikael AI, com foco em inferência local e privacidade.

## Regras do projeto

- O chat e a inferência acontecem no próprio aparelho.
- Não existe modo Cloud, WebView, Render, OpenRouter ou endpoint remoto de chat.
- Depois que um modelo GGUF é importado, o uso do chat não precisa de internet.
- Modelos ficam no armazenamento privado do aplicativo.
- O primeiro alvo de compilação é Android ARM64 (arm64-v8a).
- A primeira etapa é texto. Visão, geração de imagens, pesquisa web e ferramentas externas não são anunciadas como recursos disponíveis.

## Requisitos

- Android 9.0 (API 28) ou superior.
- ABI inicial: ARM64.
- Um modelo GGUF compatível, baixado pelo usuário e importado pelo seletor de arquivos do Android.
- Nesta etapa, o prompt usa ChatML; prefira modelos Instruct que aceitem esse formato.
- A capacidade depende do tamanho do modelo, do contexto e da memória livre. Aparelhos de entrada precisam de modelos pequenos e quantizados.

## Compilar

O projeto usa Gradle, Android NDK e CMake. O workflow GitHub Actions compila o APK de depuração e publica o arquivo como artefato.

    gradle :app:assembleDebug

O APK de depuração fica em app/build/outputs/apk/debug/app-debug.apk.

## Estado atual

A estrutura nativa, o chat local básico, o histórico e o seletor de modelos GGUF estão em implementação. O app ainda precisa passar pelo CI e ser testado em aparelho Android ARM64 real. O download integrado de modelos, streaming de tokens e múltiplas conversas ainda não estão prontos.
