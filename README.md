# Mikael AI Android — v1.1.0

Aplicativo Android nativo da Mikael AI com dois modos: online e offline. O projeto é independente do site e mantém a interface móvel inspirada no Mikael AI original.

## Interface
- Cinco abas inferiores animadas: Início, Conversas, Projetos, Arquivos e Ajustes.
- Tema escuro verde.
- Navegação e componentes adaptados para celular.
- Versão exibida no cabeçalho.

## Modo online
- Login usando a senha APP_PASSWORD configurada no servidor.
- A opção de salvar senha vem ativada por padrão; a senha é criptografada com AES-GCM e uma chave protegida pelo Android Keystore.
- A sessão usa cookie HttpOnly mantido em memória. Quando o modo online está ativo e há senha salva, o app tenta autenticar novamente ao abrir.
- Chat e histórico de conversas usam a API Mikael AI hospedada.
- Envio de documentos, código e imagens; análise de arquivos e geração de arquivos.
- Consulta de conectores GitHub/MCP. O gerenciamento e a autorização dos conectores continuam na interface web original.
- A conexão exige internet e o serviço online disponível.

## Modo offline
- Inferência local com llama.cpp via JNI/NDK.
- Importação de modelos GGUF pelo seletor de arquivos Android.
- O chat offline não envia mensagens ao servidor.
- O modelo recomendado usa ChatML. Outros modelos importados também precisam ser compatíveis com ChatML nesta versão.\n- O primeiro download do modelo recomendado exige internet; depois de baixado, a geração local não depende de Render, OpenRouter ou créditos.
- Desempenho depende do tamanho/quantização, contexto, RAM e CPU do aparelho.

## Modelos online
A API atualmente publicada oferece Mikael AI, DeepSeek V4.1 Flash e GPT-4o mini. A branch complementar feature/android-model-catalog do repositório Mikael-AI adiciona Gemini Flash Latest, Claude Sonnet Latest, Qwen3.8 Max Prime, GLM 5.3 Prime, GPT-6 Luna e GPT-6 Sol. Os novos modelos só aparecem depois que a atualização do backend for integrada e publicada no serviço online. A disponibilidade depende da configuração do provedor e pode haver consumo de créditos.

## Segurança
- A senha é criptografada no aparelho pelo Android Keystore; ela não é gravada em texto puro nas preferências.
- A chave LLM_API_KEY do provedor fica somente no servidor e nunca deve ser incluída no APK.
- O tráfego online usa HTTPS.
- Com o modo offline selecionado, o aplicativo não faz chamadas de chat à API remota.
- O backup Android do app está desativado para evitar copiar credenciais locais.

## Compilação e testes
O workflow .github/workflows/android-apk.yml executa testes unitários e lint na branch feature/native-online-hybrid-ui. Essa branch não empacota APK automaticamente; o empacotamento ocorre em main ou por execução manual do workflow.

## Limitações conhecidas
- O gerenciamento completo de GitHub/MCP ainda é feito no site original.
- O histórico local offline ainda é uma conversa salva no aparelho; o histórico online usa as conversas do servidor.
- Download integrado de modelos GGUF, streaming de tokens e visualização/baixar arquivos do servidor ainda não foram implementados.
- A compilação e o teste em aparelho Android ARM64 ainda precisam ser confirmados. Não considere esta branch pronta até o CI passar e ocorrer um teste real no dispositivo.

## Requisitos
- Android 9.0 (API 28) ou superior.
- ABI inicial: ARM64 (arm64-v8a).
- Internet somente para o modo online, upload/análise de arquivos e atualização dos dados remotos.
- Para usar offline, importe um modelo GGUF compatível.
