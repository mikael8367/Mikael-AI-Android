# Mikael AI Android

Aplicativo Android nativo com motor experimental de inferência próprio e conector GitHub opcional.

## Estado real do projeto

- Motor C++ próprio no dispositivo Android: atenção causal multi-head, RMSNorm, projeções lineares, conexões residuais e SwiGLU.
- Formato binário autoral MKAI versão 1, documentado em [docs/MKAI_FORMAT_V1.md](docs/MKAI_FORMAT_V1.md), com exportador de pesos treinados do zero.
- Tokenizer BPE byte-level autoral MKTOK v1, com treinamento próprio, encode/decode, persistência SHA-256 e testes; veja [docs/MKTOK_FORMAT_V1.md](docs/MKTOK_FORMAT_V1.md).
- ABI inicial ARM64, inferência em CPU e sem serviço externo de IA.\n- Pipeline próprio de treinamento PyTorch em CPU, com inicialização aleatória, validação e exportação MKAI v1: [training/README.md](training/README.md).
- Importação local de arquivos .mkai com validação de cabeçalho.
- Catálogo de modelos de terceiros desativado. O motor não aceita GGUF.
- Conector GitHub por Device Flow, para ler repositórios e importar README como contexto.

## Limitações importantes

O motor e o pipeline de treinamento estão implementados, mas ainda não existe um corpus de treinamento no repositório nem um arquivo de pesos Mikael AI treinado. Portanto, ainda não há um assistente inteligente pronto para uso. É necessário adicionar um corpus licenciado e executar o workflow manual de treinamento descrito em [training/README.md](training/README.md).

A implementação atual usa bytes UTF-8 como IDs (256 valores), embeddings posicionais aprendidos e geração greedy; o BPE MKTOK ainda não está integrado porque requer MKAI v2. A inferência recalcula toda a sequência a cada token. Ainda faltam integrar o BPE, implementar cache KV, quantização, testes nativos automatizados e otimizações ARM64. O primeiro modelo treinado será experimental e não deve ser confundido com um assistente moderno.

O Galaxy A12 pode ser usado para inferência de um modelo pequeno e quantizado no futuro; não é adequado para treinar do zero um modelo de linguagem desse porte. O treinamento pode ser executado pelo workflow de CPU do GitHub Actions usando o navegador do celular; o aparelho não precisa treinar. A qualidade depende muito do volume, da qualidade e da licença do corpus. O modelo final poderá funcionar localmente, mas desempenho e qualidade precisam ser medidos no aparelho.

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
