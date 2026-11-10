# Mikael AI Android

Aplicativo Android nativo em Kotlin/Jetpack Compose. A interface mantém o nome Mikael AI e oferece modelos individuais e uma opção de colaboração local chamada **Mikael AI — 4 modelos**.

## Plataforma

- Android nativo, sem WebView.
- Min SDK 28 (Android 9+), target SDK 35.
- ABI publicada: `arm64-v8a`.
- Interface Jetpack Compose com navegação inferior.
- Inferência local em CPU: motor MKAI próprio e llama.cpp para arquivos GGUF.
- O modo de quatro modelos não usa API, servidor de IA ou processamento em nuvem. Mensagens, histórico compartilhado e pesos permanecem no aparelho.

## Modelos participantes

| Participante | Formato / quantização | Download aproximado | Licença declarada |
|---|---|---:|---|
| Mikael AI autoral | MKAI v1 | 0,6 MB | Não declarada |
| Qwen2.5-Coder 0.5B Instruct | GGUF Q4_K_M | 491 MB | Apache-2.0 |
| SmolLM2 360M Instruct | GGUF Q4_K_M | 271 MB | Apache-2.0 |
| TinyLlama 1.1B Chat | GGUF Q4_K_M | 622 MB | Apache-2.0 |

O conjunto completo exige aproximadamente **1,38 GB de download**, além de margem para arquivos temporários e validação. O aplicativo calcula o tamanho que falta e o espaço disponível antes de iniciar a instalação. Os pesos não são embutidos no APK e **nenhum download começa sem confirmação do usuário**. A instalação em equipe baixa um arquivo por vez, mostra progresso e permite cancelar; arquivos concluídos são preservados e arquivos parciais são descartados.

O checkpoint autoral Mikael AI é um modelo experimental muito pequeno (cerca de 0,6 MB), treinado com a arquitetura MKAI do próprio projeto. Ele não tem capacidade comparável aos três modelos externos. A licença de distribuição do checkpoint não está declarada; o app o utiliza localmente e não o redistribui.

## Colaboração compartilhada

- O chat apresenta uma única conversa e uma única resposta final consolidada.
- Os quatro participantes são chamados em sequência. Cada chamada recebe o pedido original, contexto recente do chat, a lista da equipe, quem já falou, quem ainda não falou e as contribuições anteriores.
- Cada modelo pode responder a uma proposta anterior, discordar com justificativa objetiva, pedir uma verificação ou sugerir correção.
- Depois das quatro contribuições, uma etapa final (preferencialmente Qwen) recebe o histórico completo, revisa criticamente as propostas, corrige erros e consolida uma resposta.
- Limite: quatro chamadas de contribuição + uma chamada de síntese (máximo de cinco por mensagem). Contribuições e resposta final têm limites curtos de tokens.
- Se algum modelo falhar, não será contado como participante. O chat informa a participação parcial. Se a síntese falhar, é usada a melhor contribuição disponível e isso também é informado.
- Não é execução simultânea: por segurança de memória, o app descarrega o modelo anterior antes de carregar o próximo. Todos compartilham o transcript, mas trabalham em sequência.
- O modo de equipe não aciona pesquisa web nem conectores GitHub. Não envia mensagens para serviços externos.

## Compatibilidade, RAM e desempenho

- Os três modelos externos são distribuídos em GGUF; o runtime llama.cpp do projeto é compilado para Android ARM64. O código nativo aplica o template de conversa do GGUF e possui fallback de template específico para TinyLlama.
- O runtime está fixado na revisão `281e7dad7cfe4539b13d0218f4b0fd5a03f3abc9`. A compilação confirma a integração nativa; a compatibilidade final de cada arquivo e o desempenho precisam ser validados em execução no aparelho.
- O aparelho-alvo é um Galaxy A12 com cerca de 4 GB de RAM. O app carrega somente um modelo por vez, usa contexto de 1024 tokens no modo de equipe e reavalia a memória disponível antes de cada carga.
- O TinyLlama é o participante mais pesado. O orçamento interno pode exigir configuração de 1024 MiB ou mais, e ainda assim o Android pode recusar o carregamento se outros apps estiverem usando memória. O checkpoint MKAI atual usa tokens de byte e só tem 512 bytes de contexto; se a solicitação original e o contexto não couberem sem truncamento, ele será ignorado e não contará como participante. Um modelo que não carregar é marcado como falha; o app não afirma que os quatro participaram.
- O modelo é executado em CPU. Cinco chamadas sequenciais podem levar bastante tempo e aumentar uso de bateria e temperatura. Não há benchmark confiável de velocidade, temperatura ou duração da bateria no Galaxy A12; esses dados dependem do estado do aparelho e precisam ser medidos nele.
- O orçamento de RAM é uma proteção aproximada, não substitui medição de pico nem garante que o Android não encerre o processo.
- A memória de um modelo não é somada à dos outros, pois cada motor é descarregado antes da próxima chamada.

## Inteligência de programação

- O prompt usa orientações específicas para depuração, implementação, revisão de código e explicações técnicas.
- Tarefas de programação reservam até 256 tokens para respostas individuais, respeitando o limite do runtime e do contexto.
- As orientações exigem separar evidências de hipóteses, não inventar execução/testes e revisar segurança, compatibilidade, concorrência e casos de borda.
- A colaboração pode ampliar a revisão de propostas, mas não transforma modelos pequenos em modelos grandes nem garante código correto. Sempre revise e teste alterações importantes.

## Recursos e privacidade

- Histórico, memória e contexto de conversas anteriores armazenados localmente e criptografados com Android Keystore.
- Memória compartilhada pelo pipeline de prompt; preferências e fatos técnicos explícitos podem ser aprendidos automaticamente quando o usuário ativa essa opção.
- Aprendizado automático desligado por padrão; ignora categorias sensíveis e dados com aparência de credenciais/contatos.
- O modo de equipe usa somente os pesos locais e o contexto local. Não envia conversas, arquivos ou prompts a APIs, servidores ou serviços externos.
- Nos modos individuais, ferramentas online existentes continuam separadas e exigem autorização explícita. Elas não são usadas pelo modo de quatro modelos.
- Execução de código não está implementada; o app pode escrever e revisar código, mas não deve afirmar que executa programas.

## Compilação e validação

Com JDK 17, Android SDK 35, NDK 27.2.12479018 e CMake 3.22.1:

```bash
gradle :app:externalNativeBuildDebug
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:assembleDebug
```

A CI executa compilação nativa ARM64, testes unitários e Android Lint. Build bem-sucedido não prova que todos os modelos foram carregados ou que geraram respostas corretas no aparelho; isso requer teste manual no dispositivo. Este projeto não gera APK automaticamente sem solicitação explícita.

## Licenças

- llama.cpp: MIT.
- Qwen2.5-Coder-0.5B-Instruct: Apache-2.0.
- SmolLM2-360M-Instruct: Apache-2.0.
- TinyLlama-1.1B-Chat-v1.0: Apache-2.0.
- Mikael AI MKAI v1: licença de distribuição não declarada.
- Os pesos são baixados separadamente e não são incorporados ao APK. Consulte as fontes upstream e suas licenças antes de redistribuir qualquer peso.
