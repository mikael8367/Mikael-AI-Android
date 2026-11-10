# Mikael AI Android

Aplicativo Android nativo da Mikael AI, com inferência local e integração opcional com GitHub.

## Recursos

- Chat e inferência com modelos GGUF no próprio aparelho.
- Modelos armazenados no espaço privado do aplicativo.
- Conector GitHub nativo por Device Flow do GitHub App.
- Lista de repositórios que o GitHub App instalado pode acessar.
- Importação do README selecionado para o contexto do chat local.
- Token de acesso protegido por AES-GCM com chave mantida no Android Keystore.

## Privacidade e permissões

- O chat continua local: mensagens e prompts não são enviados a um servidor de IA.
- O conector GitHub acessa a API oficial do GitHub somente quando o usuário abre a aba GitHub e autoriza a conta.
- O aplicativo não contém App Secret nem chave privada do GitHub App.
- O token de acesso é criptografado no dispositivo. Desconectar remove a cópia local; para revogar a autorização também é necessário removê-la nas configurações de aplicações autorizadas do GitHub.
- O que o app consegue ler depende das permissões configuradas no GitHub App e dos repositórios nos quais ele foi instalado.

## Configurar o conector GitHub

1. Nas configurações do GitHub App, habilite **Enable Device Flow**.
2. Configure as permissões mínimas necessárias: **Metadata: Read-only** e **Contents: Read-only**.
3. Instale o GitHub App apenas nos repositórios que você deseja disponibilizar ao aplicativo.
4. Na aba **GitHub** da Mikael AI, informe o **Client ID** do GitHub App (não o App ID e nunca o Client Secret).
5. Toque em **Conectar ao GitHub**, abra a página oficial indicada, informe o código e autorize.
6. Selecione um repositório e importe o README para usá-lo como contexto no chat.

O Device Flow não usa Client Secret. Se o GitHub informar que o Device Flow está desativado ou que o Client ID está incorreto, revise as configurações do GitHub App.

## Requisitos

- Android 9.0 (API 28) ou superior.
- ABI inicial: ARM64 (arm64-v8a).
- Um modelo GGUF compatível, importado pelo seletor de arquivos do Android.
- O prompt local usa ChatML; prefira modelos Instruct que aceitem esse formato.
- O desempenho depende do tamanho do modelo, do contexto e da memória livre.

## Compilar

O projeto usa Gradle, Android NDK e CMake. O workflow GitHub Actions compila o APK de depuração e publica o arquivo como artefato.

    gradle :app:assembleDebug

O APK de depuração fica em app/build/outputs/apk/debug/app-debug.apk.

## Limitações atuais do conector

- A versão inicial lista os repositórios e importa o README; ainda não edita arquivos nem cria commits ou pull requests.
- A listagem e a leitura são limitadas às permissões concedidas ao GitHub App.
- O token pode expirar conforme a configuração do GitHub App; se isso acontecer, será necessário conectar novamente nesta versão.

## Estado

A estrutura nativa, o chat local, o histórico, o seletor GGUF e o conector GitHub estão em implementação. O projeto precisa passar pelo CI e ser testado em aparelho Android ARM64 real.
