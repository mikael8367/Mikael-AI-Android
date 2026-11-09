# Mikael AI Android — v1.4.0

Aplicativo Android nativo da Mikael AI com dois modos: online e offline. O projeto é independente do site e mantém a interface móvel inspirada no Mikael AI original.

## Interface
- Cinco abas inferiores animadas: Início, Conversas, Projetos, Arquivos e Ajustes.
- Tema escuro verde.
- Modos de conversa: Normal, Programar e Auditoria profunda.
- Modo Programar orienta a IA a produzir código completo, indicar arquivos, dependências, compatibilidade e testes sem alegar execução inexistente.
- Auditoria profunda solicita análise sistemática de lógica, estados, erros, segurança, desempenho, memória, concorrência e regressões; achados devem incluir severidade, localização, reprodução, causa raiz, impacto e correção.
- O contexto recente é ampliado e limitado para caber no modelo local; os modos técnicos usam orçamento de até 768 tokens de saída.
- Navegação e componentes adaptados para celular.
- Versão exibida no cabeçalho.

## Modo online
- Login usando a senha APP_PASSWORD configurada no servidor.
- A opção de salvar senha vem ativada por padrão; a senha é criptografada com AES-GCM e uma chave protegida pelo Android Keystore.
- A sessão usa cookie HttpOnly mantido em memória. Quando o modo online está ativo e há senha salva, o app tenta autenticar novamente ao abrir.
- O modo online usa realmente o endpoint `/api/chat` e o modelo escolhido no servidor. **Mikael AI** (`mikael`) é a opção principal selecionada por padrão; também são exibidos os outros modelos publicados pelo backend. É necessário entrar na conta e ter o provedor configurado no Render.
- A seleção Online/Offline fica salva entre inicializações. Ao reabrir o app, uma sessão online salva tenta reconectar com a senha protegida pelo Android Keystore.
- Envio de documentos, código e imagens; análise de arquivos e geração de arquivos.
- Tela nativa de Conectores aberta por Ajustes: pesquisa, recomendações de GitHub, Notion, Google Drive, Slack, PostgreSQL e arquivos, cadastro de MCP personalizado, teste, ativação/desativação e remoção. O cadastro usa os endpoints existentes do backend; o servidor MCP deve ter URL HTTPS pública. O fluxo OAuth do GitHub continua disponível pelo site.
- A pesquisa web exige conexão Wi-Fi ou dados móveis e disponibilidade do buscador DuckDuckGo; não exige senha da Mikael AI nem chave de API.

## Modo offline
- Inferência local com llama.cpp via JNI/NDK.
- Importação de modelos GGUF pelo seletor de arquivos Android.
- No modo Offline, o chat usa exclusivamente o GGUF selecionado e não envia mensagens ao servidor. No modo Online, o texto é enviado ao backend para gerar a resposta com o modelo remoto selecionado.
- Download integrado do modelo recomendado Qwen2.5 0.5B Instruct Q4_K_M, com verificação SHA-256, ou importação de outros modelos GGUF. Os modelos baixados/importados aparecem como seletores no topo do chat offline.
- O primeiro download exige internet; depois de instalado, a geração local não depende de Render, OpenRouter ou créditos.
- O modelo recomendado usa ChatML. Outros modelos importados também precisam ser compatíveis com ChatML nesta versão. O contexto padrão foi reduzido para 512 tokens para favorecer aparelhos com pouca RAM; a tela informa a estimativa e o orçamento de memória quando o carregamento é recusado.
- Desempenho depende do tamanho/quantização, contexto, RAM e CPU do aparelho.

## Modelos online
O modelo `mikael` é um perfil do backend que usa o modelo configurado pela variável `LLM_MODEL` no Render. Ele não é um arquivo GGUF distribuído no APK e, portanto, não pode ser baixado para uso offline como o Qwen. A lista do app é obtida de `/api/models`; a disponibilidade depende do backend e da configuração do provedor. O uso remoto pode consumir créditos.

## Segurança
- A senha é criptografada no aparelho pelo Android Keystore; ela não é gravada em texto puro nas preferências.
- A chave LLM_API_KEY do provedor fica somente no servidor e nunca deve ser incluída no APK.
- Tokens de conectores MCP são enviados ao backend autenticado e armazenados criptografados pelo servidor; o app exige URL HTTPS pública e não aceita endereços locais.
- O tráfego online usa HTTPS.
- A geração de respostas continua local. Quando a pesquisa web é ativada, somente a pergunta daquela mensagem é enviada ao buscador externo.
- O backup Android do app está desativado para evitar copiar credenciais locais.

## Compilação e testes
O workflow .github/workflows/android-apk.yml executa testes unitários, Android Lint, compilação nativa e APK debug na branch feature/native-online-hybrid-ui. O APK é publicado como artefato do GitHub Actions quando todas as etapas passam.

## Limitações conhecidas
- O OAuth do GitHub ainda é iniciado pelo site original. Conectores MCP personalizados podem ser gerenciados no app, mas exigem um servidor MCP próprio ou de terceiros hospedado em HTTPS.
- O histórico local offline ainda é uma conversa salva no aparelho; o histórico online usa as conversas do servidor.
- Streaming de tokens e visualização/baixar arquivos do servidor ainda não foram implementados.
- A compilação e o teste em aparelho Android ARM64 ainda precisam ser confirmados. Não considere esta branch pronta até o CI passar e ocorrer um teste real no dispositivo.

## Requisitos
- Android 9.0 (API 28) ou superior.
- ABI inicial: ARM64 (arm64-v8a).
- Internet para a pesquisa web opcional, download inicial do modelo e recursos remotos.
- Para usar offline, baixe o modelo recomendado ou importe um modelo GGUF compatível.
