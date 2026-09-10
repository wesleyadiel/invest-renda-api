# Collection Bruno - invest-renda-api

Testada de ponta a ponta via `@usebruno/cli` contra a API local (28/28
requests, 53/53 assertions, execução idempotente - pode rodar quantas
vezes quiser). A execução completa leva ~9s (a pasta Observabilidade
espera de verdade o retry da fila se esgotar antes de checar a DLQ).

## Importar no Bruno (GUI)

Bruno não usa um único arquivo de "import" como o Postman - a collection
já é essa pasta. No app: **Open Collection** → selecione a pasta `bruno/`
(a que contém este README e o `bruno.json`).

Depois selecione o ambiente **Local** no seletor de ambiente (canto
superior direito) antes de rodar qualquer request - ele define
`baseUrl: http://localhost:8080`.

## Pré-requisitos

A API e a infra (Postgres, Redis, RabbitMQ) precisam estar no ar:

```bash
cd ../docker && docker compose up -d
cd .. && ./mvnw spring-boot:run
```

## Ordem recomendada

As pastas são numeradas na ordem pensada para rodar a collection inteira
de cima para baixo (botão "Run" na pasta raiz, ou `bru run -r`):

1. **00.Sanity** - confere se a API está de pé
2. **01.Produtos** - cria um produto e guarda o `produtoId`
3. **02.Contas** - cria uma conta (CPF válido gerado sozinho a cada
   execução) e guarda `contaCpf`/`contaId`
4. **03.Aplicacoes** - usa `produtoId` + `contaCpf` para solicitar uma
   aplicação e guarda `aplicacaoId`
5. **04.Observabilidade** - status da fila, aciona a simulação de falha,
   solicita uma aplicação dedicada, espera de verdade o retry se esgotar
   (`bru.sleep(7000)` no post-response de "Aguardar retry (7s)"), lista
   a DLQ, reprocessa a mensagem e confere o cache - fluxo de ponta a
   ponta sem nenhum passo manual. O stream de logs (SSE) também mora
   aqui, marcado com a tag `streaming` (veja abaixo)
6. **05.Integracoes-Externas** - cotação do dólar, feature toggle,
   circuit breaker
7. **06.Limpeza** - exclui o produto e a conta criados. Fica por último
   de propósito: como já existe uma aplicação associada a essa altura,
   a API bloqueia a exclusão (422) - é o comportamento esperado, não um
   erro da collection.

Cada request tem um bloco `docs` explicando pré-requisitos e o que
esperar da resposta.

## Rodando via CLI

```bash
npx @usebruno/cli run --env Local -r --exclude-tags=streaming
```

`--exclude-tags=streaming` pula o request de stream de logs (SSE) - ele
nunca "termina" sozinho, então trava uma execução em lote. Teste-o à
parte, manualmente, ou prefira a página `/logs` do frontend
(`invest-renda-web`) para ver os logs ao vivo.
