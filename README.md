# Mikael AI Android — edição local

Aplicativo Android nativo. A geração de respostas acontece no próprio aparelho com llama.cpp e um modelo GGUF selecionado pelo usuário.

## O que é local
- Chat, inferência e histórico armazenado no aparelho.
- Perfil Mikael AI com instruções especializadas em programação, modo normal e auditoria profunda.
- Modelos GGUF importados para o armazenamento privado do aplicativo.
- Motor llama.cpp integrado por JNI/NDK para ARM64.
- Abas inferiores animadas: Chat, Modelos e Configurações.
- Orçamento configurável de memória e contexto, com verificação antes de carregar o modelo.
- Cancelamento de geração e opção de descarregar/excluir modelos.

## Internet
A internet não é usada para gerar respostas, enviar conversas ou consultar um provedor de IA. Ela só é necessária se o usuário tocar em "Baixar modelo recomendado". Também é possível baixar um GGUF por outro meio e importá-lo pelo seletor de arquivos do Android; depois disso, chat e histórico funcionam sem internet.

## Modelo Mikael AI
"Mikael AI" é o perfil local: instruções, modos de resposta e parâmetros aplicados ao modelo GGUF escolhido. Os pesos de linguagem vêm do modelo-base importado/baixado; o prompt não é um treinamento de pesos feito do zero. O modelo recomendado é Qwen2.5 0.5B Instruct Q4_K_M, escolhido como ponto de partida leve para celulares com pouca RAM. Modelos de programação maiores podem responder melhor, mas podem ficar lentos ou não caber no Galaxy A12.

O formato de prompt desta versão usa ChatML. Modelos importados devem ser compatíveis com esse formato.

## Privacidade
- Nenhuma mensagem do chat é enviada ao Render, OpenRouter ou qualquer endpoint remoto de IA.
- Não há login, senha de servidor, seleção de modelos online ou histórico remoto na interface principal.
- O histórico é salvo em SharedPreferences locais do aplicativo.
- O backup Android está desativado.
- A permissão INTERNET é usada somente pelo download opcional do arquivo GGUF.

## Requisitos
- Android 9.0 (API 28) ou superior.
- ABI inicial: arm64-v8a.
- Espaço livre suficiente para o arquivo GGUF (o recomendado ocupa aproximadamente 469 MiB).
- RAM livre suficiente para o modelo, contexto e buffers nativos.

## Compilação e validação
O workflow desta branch executa testes unitários, Android Lint e compilação das bibliotecas nativas. Ele não gera APK automaticamente. Um APK só deve ser gerado quando solicitado explicitamente.

Comandos de validação no ambiente de desenvolvimento:
- `gradle :app:testDebugUnitTest`
- `gradle :app:lintDebug`
- `gradle :app:externalNativeBuildDebug`

## Limitações
- A inferência é texto; visão, geração de imagens, pesquisa web e ferramentas externas não estão incluídas nesta edição.
- O desempenho depende do modelo, quantização, contexto, RAM e CPU.
- A compilação CI não substitui teste físico. Esta branch ainda precisa passar pelo CI e ser validada em um Android ARM64 real antes de ser considerada pronta.
