# Mikael AI Android

Aplicativo Android nativo da Mikael AI, com foco em inferência local e privacidade.

## Regras do projeto

- O chat e a inferência acontecem no próprio aparelho.
- Não existe modo Cloud, WebView, Render, OpenRouter ou endpoint remoto de chat.
- Depois que um modelo GGUF é importado, o uso do chat não precisa de internet.
- Modelos ficam no armazenamento privado do aplicativo.
- O primeiro alvo de compilação é Android ARM64 (arm64-v8a).
- A primeira etapa é texto. Visão, geração de imagens, pesquisa web e ferramentas externas não são anunciadas como recursos disponíveis.

## Requisitos

- Android 8.0 (API 26) ou superior.
- ABI inicial: ARM64.
- Um modelo GGUF compatível, importado pelo seletor de arquivos do Android.
- A capacidade depende do tamanho do modelo, do contexto e da memória livre. Aparelhos de entrada precisam de modelos pequenos e quantizados.

## Compilar

O projeto usa Gradle, Android NDK e CMake. O workflow GitHub Actions compila o APK de depuração e publica o arquivo como artefato.

    gradle :app:assembleDebug

O APK de depuração fica em app/build/outputs/apk/debug/app-debug.apk.

## Estado atual

A estrutura nativa e o pipeline de compilação estão sendo montados. A integração completa de inferência, persistência de conversas e gerenciamento de modelos será validada em etapas. Não considere o app pronto até o CI passar e ocorrer um teste em aparelho Android ARM64 real.
