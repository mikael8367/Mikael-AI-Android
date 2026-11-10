# Mikael AI Android

Aplicativo Android nativo em Kotlin/Jetpack Compose, com motores locais C++ via JNI/NDK.

## Plataforma e arquitetura

- Android nativo, sem WebView.
- Min SDK 28 (Android 9+), target SDK 35.
- ABI publicada: `arm64-v8a`.
- Interface Jetpack Compose com navegação inferior.
- Motor autoral MKAI v1 e runtime GGUF separado baseado em llama.cpp, fixado em um commit específico.
- Inferência local em CPU; não exige servidor de IA para conversar depois que o modelo estiver instalado.

## Modelos disponíveis

Os pesos não são embutidos no APK. O app mostra tamanho, licença, espaço livre e estimativa de RAM antes do download; a instalação só começa após confirmação. O download é cancelável e o arquivo parcial é descartado se for interrompido. Modelos GGUF passam por verificação de cabeçalho e SHA-256 fornecido pelo repositório antes de serem promovidos para instalados.

| Modelo | Formato | Download aproximado | Licença | Observação |
|---|---|---:|---|---|
| Mikael AI Base | MKAI v1 float32 | 591.144 bytes no release atual | Autoral experimental | Modelo pequeno treinado com corpus didático; não é um assistente avançado |
| Qwen2.5-0.5B-Instruct | GGUF Q4_K_M | 0,40 GB | Apache-2.0 | Melhor ponto de partida do catálogo para português e uso multilíngue |
| SmolLM2-360M-Instruct | GGUF Q4_K_M | 0,27 GB | Apache-2.0 | Menor; o cartão oficial alerta que os modelos SmolLM2 entendem e geram principalmente inglês |
| TinyLlama-1.1B-Chat-v1.0 | GGUF Q4_K_M | 0,622 GB | Apache-2.0 | Mais pesado; pode exceder o orçamento de RAM em aparelhos modestos |

Os tamanhos dos GGUF são aproximados e podem mudar conforme a revisão publicada na origem. Fontes e licenças são mostradas no catálogo e nos cartões oficiais dos modelos. O download do Qwen Q4_K_M sozinho já ocupa mais de 200 MB; o APK permanece separado e muito menor. O tamanho real do APK e o espaço pós-instalação precisam ser medidos em cada build/dispositivo, não inferidos a partir do tamanho do modelo.

## Hardware e memória

- O runtime GGUF usa llama.cpp em CPU ARM64.
- Antes de carregar, o app consulta a memória disponível do Android e aplica um orçamento configurável.
- A estimativa de RAM é uma proteção aproximada, não substitui medição de pico no aparelho.
- O Galaxy A12 é um dispositivo de recursos limitados. Qwen2.5-0.5B Q4_K_M é a primeira opção a testar; velocidade, temperatura, estabilidade e consumo real ainda precisam ser medidos fisicamente.
- Não declarar compatibilidade prática ou desempenho confirmado sem teste no aparelho.

## Offline e recursos online

- Chat local: funciona offline quando um modelo compatível está instalado e carregado.
- Histórico, memória e tarefas interrompidas: armazenados localmente e criptografados com AES-GCM usando uma chave não exportável do Android Keystore.
- Downloads de modelos: exigem internet e confirmação explícita.
- Conector GitHub: exige conexão e autorização do usuário; o token é protegido com Android Keystore.
- Pesquisa na web pela aba Pesquisa, usando o backend Mikael AI no Render com OpenRouter + Exa. A cada busca, o usuário confirma o envio; somente o texto digitado no campo é transmitido, sem histórico, memória, arquivos ou código privado. O uso pode consumir créditos/limites do provedor.
- A sessão do Render é armazenada criptografada com Android Keystore; a senha não é persistida no app.
- Execução de código ainda não está implementada. O app pode ajudar a escrever e revisar código, mas não deve afirmar que executa programas.

## Treinamento autoral

O pipeline `training/` treina um Transformer experimental do zero e exporta MKAI v1. O corpus incluído é didático e pequeno. O modelo MKAI v1 usa bytes UTF-8 como IDs, não integra o tokenizer BPE MKTOK, não possui cache KV e recalcula a sequência a cada token. A qualidade é limitada; não deve ser confundido com modelos comerciais ou assistentes modernos.

## Privacidade e armazenamento

- Modelos ficam em armazenamento privado do app e podem ser excluídos na aba Modelos.
- O app não precisa de permissão ampla de arquivos para armazenar modelos no próprio diretório.
- Conversas não são enviadas para inferência remota.
- Chaves de API de serviços externos não devem ser colocadas no APK. Nenhum serviço geral de pesquisa está configurado nesta branch.

## Compilação e validação

Com JDK 17, Android SDK 35, NDK 27.2.12479018 e CMake 3.22.1:

```bash
gradle :app:externalNativeBuildDebug
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:assembleDebug
```

A CI da branch executa compilação nativa ARM64, testes unitários e Android Lint. A geração de APK é uma etapa separada e condicional; esta revisão solicita um único APK debug para medir o pacote e permitir teste manual. Build bem-sucedido não prova que o modelo foi carregado ou que gerou resposta em um Galaxy A12; isso requer teste físico.

## Licenças de terceiros

- llama.cpp: MIT, conforme o repositório upstream fixado no CMake.
- Qwen2.5-0.5B-Instruct, SmolLM2-360M-Instruct e TinyLlama-1.1B-Chat-v1.0: Apache-2.0, conforme os cartões dos modelos.
- Os arquivos de pesos são obtidos diretamente dos repositórios indicados no catálogo; o app não os incorpora ao APK.
