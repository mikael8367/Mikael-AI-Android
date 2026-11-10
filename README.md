# Mikael AI Android

Aplicativo Android nativo com motor experimental de inferência próprio e conector GitHub opcional.

## Estado real do projeto

- Motor C++ próprio no dispositivo Android: atenção causal multi-head, RMSNorm, projeções lineares, conexões residuais e SwiGLU.
- Formato binário autoral MKAI versão 1, documentado em [docs/MKAI_FORMAT_V1.md](docs/MKAI_FORMAT_V1.md).
- ABI inicial ARM64, inferência em CPU e sem serviço externo de IA.
- Importação local de arquivos .mkai com validação de cabeçalho.
- Catálogo de modelos de terceiros desativado. O motor não aceita GGUF.
- Conector GitHub por Device Flow, para ler repositórios e importar README como contexto.

## Limitações importantes

O motor é a primeira implementação técnica e ainda não existe um conjunto de pesos Mikael AI treinado. Portanto, o aplicativo ainda não consegue atuar como assistente inteligente até que o tokenizer e os pesos sejam treinados do zero e exportados para MKAI.

A implementação atual usa tokenizer byte-level provisório, embeddings posicionais aprendidos, geração greedy e recalcula toda a sequência a cada token. Ainda faltam treinamento, cache KV, quantização, testes nativos automatizados e otimizações ARM64. Não confunda motor implementado com modelo treinado.

O Galaxy A12 pode ser usado para inferência de um modelo pequeno e quantizado no futuro; não é adequado para treinar do zero um modelo de linguagem desse porte. O treinamento exigirá infraestrutura computacional separada, mas o modelo final poderá funcionar integralmente no celular.

## Privacidade

- Prompts e geração local não são enviados a um servidor de IA.
- O GitHub só é acessado quando o usuário conecta a conta e solicita uma operação.
- O token do GitHub é protegido com Android Keystore.

## Configurar GitHub

1. Habilite Device Flow nas configurações do GitHub App.
2. Configure Metadata: Read-only e Contents: Read-only.
3. Instale o GitHub App apenas nos repositórios necessários.
4. Informe o Client ID na aba GitHub e autorize pelo código exibido.

## Compilação e validação

O projeto usa Gradle, Android NDK e CMake. O workflow compila o motor C++ ARM64, executa testes unitários e lint em branches de funcionalidade. A geração de APK permanece restrita à branch principal e não será solicitada nesta etapa.
