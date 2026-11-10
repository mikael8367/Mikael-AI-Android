# Mikael AI — plano técnico MKAI v2 e limite de 5 GB

## Objetivo

Evoluir o modelo autoral Mikael AI para que os arquivos de pesos instaláveis possam ocupar até **5.000.000.000 bytes (5 GB decimais)**. O limite é de armazenamento, não uma promessa de que um telefone com pouca RAM consiga carregar o modelo inteiro.

O aplicativo deve continuar distinguindo claramente:
- tamanho do arquivo baixado;
- memória residente dos pesos;
- cache KV e buffers de inferência;
- espaço temporário necessário durante download, validação e substituição;
- capacidade real do aparelho.

## Estado de partida verificado

- MKAI v1 usa 256 IDs de byte UTF-8, embeddings posicionais aprendidos e pesos float32.
- O leitor nativo v1 limita o total a 60 milhões de floats e não integra o tokenizer BPE.
- MKTOK v1 existe separadamente, mas ainda não está conectado ao Transformer.
- O treino atual inicializa pesos aleatórios e depende de corpus pequeno. Isso valida o pipeline, mas não constitui um assistente de programação competente.
- O motor C++ recalcula o contexto inteiro e ainda não tem cache KV.
- Não existe justificativa para declarar o modelo atual como IA avançada ou funcional em nível de produção.

## Arquitetura planejada

### MKAI v2
- Cabeçalho versionado com validação estrita de dimensões, offsets, tamanhos, checksum e versão do tokenizer.
- Vocabulário configurável e sincronizado com um arquivo MKTOK específico.
- RoPE para posição, com suporte a contexto configurável.
- GQA (Grouped-Query Attention) para reduzir o cache KV.
- Pesos quantizados Q4 e Q8 com escalas por bloco; FP16 poderá ser mantido como formato de referência.
- Arquivos com seções identificadas e limites explícitos, para permitir validação antes de alocar memória.
- Exportador e leitor independentes com testes de compatibilidade cruzada.

### Runtime Android
- Cache KV incremental, cancelamento confiável e liberação de memória.
- Carregamento em etapas, com verificação de espaço livre antes do download.
- Nunca descompactar um modelo de vários GB em RAM.
- Importação para arquivo temporário, SHA-256 e substituição atômica.
- Perfis de execução conforme RAM disponível; o Galaxy A12 precisa de um modelo menor e quantizado, não do modelo de 5 GB.
- Se a memória estimada ultrapassar o orçamento seguro, bloquear o carregamento com explicação clara em vez de travar o aplicativo.

### Treinamento autoral
- Continuar treinando pesos do zero; não trocar o nome de pesos de terceiros.
- Corpus amplo, diversificado, deduplicado, revisado e com licença documentada.
- Separar treino, validação e avaliação sem vazamento de exemplos.
- Incluir tarefas reais de programação, depuração, explicação, testes, Android, Java, Kotlin, C++, Python, Git, APIs e segurança defensiva.
- Medir perplexidade e avaliações práticas; registrar dados, configuração, seed, tokenizer e checksum em cada checkpoint.
- O pipeline de CPU do GitHub Actions serve para protótipos pequenos. Treinar um modelo de vários GB com qualidade exige infraestrutura de treinamento e dados muito maiores; não se deve prometer isso com um runner gratuito de CPU.

## Fases e critérios de aceite

1. **Fundação e orçamento**
   - Estimador de parâmetros, tamanho Q4/Q8/FP16/FP32 e cache KV.
   - Testes para GQA, limites, arredondamento e configurações inválidas.
2. **Formato MKAI v2**
   - Especificação binária, parser seguro, checksum e testes com arquivos truncados, adulterados e dimensões extremas.
3. **Tokenizer integrado**
   - O modelo registra o hash exato do MKTOK usado no treinamento.
   - Testes de round-trip em português, símbolos, código e UTF-8 inválido.
4. **Runtime**
   - Cache KV, quantização real, cancelamento, unload, recuperação de falhas e benchmarks ARM64.
5. **Dados e treinamento**
   - Dataset licenciado, métricas reproduzíveis, checkpoint selecionado por validação e testes de capacidade.
6. **Produto**
   - Gerenciador de modelos, downloads retomáveis, integridade, espaço livre, compatibilidade do aparelho e mensagens de erro úteis.
7. **Auditoria final**
   - Testes unitários, integração, fuzzing do parser, análise estática, lint, compilação nativa, regressões e testes em dispositivos reais.

## Segurança e integridade

- Não carregar arquivos apenas pela extensão.
- Validar tamanho máximo antes de alocar ou copiar.
- Rejeitar overflow aritmético, dimensões incompatíveis, seções sobrepostas e dados extras.
- Verificar SHA-256 do arquivo completo e hash do tokenizer.
- Não substituir o modelo funcional até que o novo arquivo tenha sido validado.
- Não incluir segredos, dados pessoais ou material sem licença no corpus.

## Limites e honestidade técnica

Um arquivo de 5 GB não significa 5 GB de RAM. Um modelo quantizado ainda precisa de memória para cache KV, buffers, contexto e o próprio Android. O Galaxy A12 não é alvo para executar localmente um modelo completo de 5 GB; ele precisa de uma configuração menor.

Também não existe garantia literal de 100% sem falhas. A meta de engenharia é reduzir riscos, testar casos extremos e só marcar cada etapa como concluída quando os critérios de aceite forem aprovados.
