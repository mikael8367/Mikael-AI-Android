# Mikael AI Android

Aplicativo Android nativo em Kotlin/Jetpack Compose. O nome e a identidade do aplicativo são Mikael AI; o único modelo de conversa distribuído pelo catálogo é Qwen2.5-Coder 0.5B Instruct.

## Plataforma

- Android nativo, sem WebView.
- Min SDK 28 (Android 9+), target SDK 35.
- ABI publicada: `arm64-v8a`.
- Interface Jetpack Compose com navegação inferior.
- Inferência local em CPU pelo llama.cpp, fixado em uma revisão específica.
- Depois de instalar os pesos, o chat local funciona sem servidor de IA.

## Modelo principal

| Modelo | Formato | Tamanho aproximado | Licença |
|---|---|---:|---|
| Qwen2.5-Coder-0.5B-Instruct | GGUF Q4_K_M | 491 MB | Apache-2.0 |

O catálogo oferece somente o Qwen2.5-Coder como modelo de conversa. O antigo checkpoint experimental MKAI não é mais selecionável nem usado para gerar respostas. O nome Mikael AI permanece como nome do aplicativo e assistente.

Os pesos não são embutidos no APK. O aplicativo mostra tamanho, licença, espaço livre e estimativa de RAM antes do download; a instalação exige confirmação, pode ser cancelada e valida o cabeçalho GGUF e o SHA-256 fixado antes de ativar o arquivo. O download vem diretamente do repositório oficial indicado no catálogo.

O modelo tem 0,5 bilhão de parâmetros e é uma base compacta para programação, não um modelo de grande porte. A qualidade em português, velocidade, temperatura e consumo real precisam ser avaliados no aparelho; instruções aprimoradas não alteram os pesos nem garantem desempenho equivalente a modelos maiores.

## Hardware e memória

- O runtime GGUF usa llama.cpp em CPU ARM64.
- Antes de carregar, o app consulta a memória disponível do Android e aplica um orçamento configurável.
- A estimativa de RAM é uma proteção aproximada, não substitui medição de pico no aparelho.
- O Qwen Q4_K_M ocupa aproximadamente 491 MB em armazenamento. O orçamento de RAM depende também do contexto e dos buffers do runtime.
- Não declarar compatibilidade prática ou desempenho confirmado sem teste físico no dispositivo.

## Recursos e privacidade

- Chat local offline após instalar e carregar o Qwen.
- Histórico e memória do usuário armazenados localmente.
- Download do modelo exige internet e confirmação explícita.
- Conector GitHub exige conexão e autorização do usuário.
- Recursos online devem identificar os dados transmitidos e pedir confirmação antes de compartilhar conteúdo privado.
- Chaves de API não devem ser colocadas no APK.
- Execução de código não está implementada; o app pode escrever e revisar código, mas não deve afirmar que executa programas.

## Compilação e validação

Com JDK 17, Android SDK 35, NDK 27.2.12479018 e CMake 3.22.1:

```bash
gradle :app:externalNativeBuildDebug
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:assembleDebug
```

A CI executa compilação nativa ARM64, testes unitários e Android Lint. Build bem-sucedido não prova que o modelo foi carregado ou que gerou respostas corretas no aparelho; isso requer teste manual no dispositivo.

## Licenças

- llama.cpp: MIT.
- Qwen2.5-Coder-0.5B-Instruct: Apache-2.0.
- Os pesos são baixados separadamente e não são incorporados ao APK. Consulte a licença da origem antes de redistribuir os pesos.
