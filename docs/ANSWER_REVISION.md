# Revisão autônoma de respostas

A Mikael AI possui uma etapa interna de revisão antes de apresentar uma resposta no chat. Não há botão, aba ou ação nova para o usuário.

## Fluxo

1. O motor local gera um rascunho com base na conversa.
2. O controlador envia ao mesmo motor uma solicitação curta de autocrítica, com o pedido original e o rascunho.
3. O modelo deve escolher explicitamente:
   - MANTER: não encontrou um problema concreto; o rascunho é apresentado.
   - REVISAR: encontrou erro factual, contradição, código inconsistente ou requisito não atendido.
4. Somente quando a resposta começa com REVISAR, o motor gera uma versão corrigida.
5. A versão corrigida substitui o rascunho; a etapa interna nunca aparece no histórico da conversa.

## Segurança e limites

- Tudo é executado pelo mesmo motor local; não há chamada a serviço externo de IA.
- A revisão tem limite de tokens e respeita o contexto configurado no modelo.
- Textos longos são limitados em bytes UTF-8 sem cortar caracteres multibyte.
- Contextos pequenos podem pular a revisão para evitar exceder a janela.
- Se a revisão falhar, for ambígua ou a correção vier vazia, a resposta inicial é preservada.
- O processo tem no máximo três gerações: rascunho, avaliação e uma correção. Não existe ciclo infinito.
- A etapa interna não cria botões nem altera a interface.

## Estado real

O controlador e os testes unitários estão no aplicativo. A qualidade da autocrítica depende dos pesos treinados e da capacidade do modelo de seguir o formato MANTER/REVISAR. O repositório ainda não possui pesos próprios treinados; portanto, não é correto afirmar que a revisão inteligente já foi validada em uma conversa real. Ela precisa ser avaliada novamente depois do treinamento do modelo autoral.
