# Referência da API

Base local: `http://localhost:8080` (ver [`local-development.md`](./local-development.md)).
Swagger UI interativo em `/swagger-ui.html` quando a aplicação está rodando.

Todos os endpoints, o payload JSON (request e response) e as mensagens de
erro estão em português — ver [`decisions.md`](./decisions.md) para o
porquê e o alcance dessa tradução (o que ficou em inglês de propósito, como
nomes de classes arquiteturais e headers HTTP padrão).

## Rate limit

Todos os endpoints abaixo (`/produtos`, `/contas`, `/aplicacoes`,
`/operacoes`) são limitados a **120 requisições/minuto por cliente** (IP),
com rajada até esse mesmo limite — configurável via
`investrenda.limiterequisicoes.*` no `application.yml`. Ao exceder,
`429 Too Many Requests`:

```json
{
  "sucesso": false,
  "dados": null,
  "mensagem": "Limite de requisicoes excedido. Tente novamente em 4s.",
  "erros": []
}
```

Toda resposta (dentro ou fora do limite) traz o header
`X-RateLimit-Remaining`; a de bloqueio também traz `Retry-After` (segundos).
`/actuator/health` não é limitado.

## Envelope de resposta

**Toda** resposta da API — sucesso ou erro, exceção tratada ou não — vem no
mesmo formato:

```json
{
  "sucesso": true,
  "dados": { "...": "..." },
  "mensagem": "Produto criado com sucesso",
  "erros": []
}
```

| Campo | Sucesso | Erro |
|---|---|---|
| `sucesso` | `true` | `false` |
| `dados` | o payload do endpoint (objeto, array, ou `null` se não houver corpo relevante) | sempre `null` |
| `mensagem` | descrição curta do que aconteceu | a mensagem de erro (da exceção, tratada ou não) |
| `erros` | `[]` | lista de erros — só tem mais de um item em erro de validação (um item por campo inválido); nos demais casos vem `[]` e a causa única fica em `mensagem` |

O **status HTTP** continua sendo o sinal principal de sucesso/falha (200,
201, 202, 400, 404, 409, 422, 429, 500 — ver `ManipuladorGlobalExcecao`); o
`sucesso`/`mensagem`/`erros` no corpo é para quem consome a resposta sem
inspecionar o código de status.

Exemplo de erro de validação (`400`):

```json
{
  "sucesso": false,
  "dados": null,
  "mensagem": "Erro de validacao",
  "erros": ["cpf: CPF invalido", "nomeTitular: tamanho deve estar entre 3 e 120"]
}
```

Exemplo de erro de negócio (`404`/`409`/`422`, um único motivo):

```json
{
  "sucesso": false,
  "dados": null,
  "mensagem": "Valor abaixo do minimo exigido pelo produto: 100.00",
  "erros": []
}
```

Uma exceção **não tratada explicitamente** (bug, falha de infraestrutura
etc.) cai no mesmo formato via um `@ExceptionHandler(Exception.class)`
catch-all: `500`, com a mensagem real da exceção em `mensagem` (não um
"erro interno" genérico) e log em nível `ERROR` no servidor com a stack
trace completa para investigação.

---

## Produtos

### `POST /produtos` — cadastrar produto

Cria um produto e invalida o cache de listagem.

```json
// request
{
  "nome": "CDB Banco Inter 120% CDI",
  "tipo": "CDB",
  "emissor": "Banco Inter",
  "classificacaoEmissor": "AA+",
  "taxaAnual": 13.5,
  "investimentoMinimo": 100.00,
  "prazoMeses": 12,
  "ativo": true            // opcional, default true
}
```

`201 Created`, `dados` = detalhe do produto criado (mesmo formato do
`GET /produtos/{id}`).

### `PUT /produtos/{id}` — editar produto

Mesmo payload do create. `200 OK` com o produto atualizado em `dados`.
Invalida tanto o cache da listagem quanto o cache do detalhe desse produto
especificamente (ver [`caching.md`](./caching.md)).

### `GET /produtos` — listar (resumo)

`200 OK`:

```json
{
  "sucesso": true,
  "dados": [
    {
      "id": "78b95996-2f1c-4738-99e0-aa30fa340002",
      "nome": "CDB Banco Inter 120% CDI",
      "tipo": "CDB",
      "taxaAnual": 13.5000,
      "investimentoMinimo": 100.00,
      "prazoMeses": 12
    }
  ],
  "mensagem": "Produtos listados com sucesso",
  "erros": []
}
```

Passa por cache Redis (chave `produtos`, TTL 10 min).

### `GET /produtos/{id}` — detalhe

`200 OK`, `dados` com todos os campos do produto (`emissor`,
`classificacaoEmissor`, `ativo`, `criadoEm`, `atualizadoEm` inclusos).
`404` se não existir. Passa por cache Redis (chave `produto::{id}`,
TTL 10 min).

### `GET /produtos/{id}/relatorio` — relatório de aplicações do produto

Traz o produto (`produto`) junto com todas as aplicações que já o
utilizaram (`aplicacoes`, mesmo formato do `GET /aplicacoes/{id}`), mais
recentes primeiro, e a contagem (`totalAplicacoes`). `404` se o produto não
existir; `aplicacoes` vem vazia (não 404) se o produto existe mas nunca foi
aplicado. **Não** passa por cache — mistura o produto (baixa volatilidade)
com o status de cada aplicação, que muda de forma assíncrona.

```json
{
  "sucesso": true,
  "dados": {
    "produto": { "id": "78b95996-...", "nome": "CDB Banco Inter 120% CDI", "...": "..." },
    "totalAplicacoes": 2,
    "aplicacoes": [
      { "id": "38d604dc-...", "produtoId": "78b95996-...", "status": "CONCLUIDA", "...": "..." }
    ]
  },
  "mensagem": "Relatorio gerado com sucesso",
  "erros": []
}
```

Implementado com `@OneToMany(mappedBy = "produto")` em `Produto` (lado
inverso do `@ManyToOne` já existente em `Aplicacao`) — `ProdutoService`
busca as aplicações via `AplicacaoRepository` e valida, antes de montar a
resposta, que o `produtoId` de cada aplicação retornada bate com o `id`
buscado; uma divergência aí é tratada como bug (500, não silenciada) em vez
de vazar dado de outro produto no relatório.

### `DELETE /produtos/{id}` — excluir produto

`200 OK`, `dados` = `null`. Invalida os mesmos caches do `PUT`. `404` se o
produto não existir. `422` se houver **aplicações associadas** a esse
produto — exclusão não é permitida nesse caso (o histórico de aplicações
não pode ficar com uma referência quebrada); é preciso que o produto
continue sem nenhuma aplicação vinculada.

---

## Contas

### `POST /contas` — abrir conta

```json
// request
{
  "cpf": "11144477735",         // 11 digitos, obrigatorio, unico, digito verificador validado
  "nomeTitular": "Maria Teste",
  "saldoInicial": 1000.00      // opcional, default 0
}
```

`201 Created`. `409 Conflict` se o CPF já tiver conta.

### `GET /contas/{cpf}` — consultar conta e saldo

`200 OK` com `dados.id`, `dados.cpf`, `dados.nomeTitular`, `dados.saldo`.
`404` se não existir.

### `GET /contas` — listar todas as contas

`200 OK`, `dados` = array no mesmo formato do `GET /contas/{cpf}`. Sem
paginação (existe para alimentar telas de validação/administração, não é um
endpoint de alto volume).

### `DELETE /contas/{cpf}` — excluir conta

`200 OK`, `dados` = `null`. `404` se a conta não existir. `422` se houver
**aplicações associadas** a essa conta — mesma regra do produto, pelo mesmo
motivo (preservar o histórico de aplicações íntegro).

---

## Aplicações (compra de investimento)

### `POST /aplicacoes` — solicitar aplicação

```json
// request
{
  "cpf": "11144477735",
  "produtoId": "78b95996-2f1c-4738-99e0-aa30fa340002",
  "valor": 300.00
}
```

`202 Accepted` (não `201`!) com `dados` = a aplicação em status `PENDENTE` e
header `Location: /aplicacoes/{id}`. O saldo **ainda não foi debitado**
nesse momento — isso acontece de forma assíncrona (ver
[`messaging.md`](./messaging.md)).

Validações síncronas (retornam erro na hora, sem passar pela fila):
- CPF sem conta → `404`
- Produto inexistente → `404`
- Produto inativo → `422`
- Valor abaixo do mínimo do produto → `422`

Validação assíncrona (só se sabe depois, consultando o status):
- Saldo insuficiente → aplicação vai para `FALHOU`

### `GET /aplicacoes/{id}` — consultar status

```json
{
  "sucesso": true,
  "dados": {
    "id": "38d604dc-6797-466c-a236-206bf9b738f6",
    "contaId": "0ff4300d-ed80-4f91-a610-81c48e3aef41",
    "contaCpf": "11144477735",
    "produtoId": "1adbc6db-79aa-4a0e-b352-0113d4267383",
    "produtoNome": "CDB Banco Inter 130% CDI",
    "valor": 300.00,
    "status": "CONCLUIDA",
    "motivoFalha": null,
    "taxaAplicada": 14.2000,
    "dataVencimento": "2027-09-08",
    "criadoEm": "2026-09-09T01:16:56.089093Z"
  },
  "mensagem": "Aplicacao encontrada",
  "erros": []
}
```

Cliente deve fazer *polling* nesse endpoint até `status` sair de
`PENDENTE`. Não existe webhook/callback nesta versão.

### `GET /aplicacoes` — listar todas as aplicações

`200 OK`, `dados` = array no mesmo formato acima, ordenado por `criadoEm`
decrescente (mais recente primeiro). `contaCpf`/`produtoNome` já vêm
resolvidos (join fetch no repositório) para não obrigar o consumidor a
cruzar com `GET /contas`/`GET /produtos` manualmente.

---

## Observabilidade (`/operacoes`)

Endpoints de apoio para validação manual — não fazem parte do domínio de
negócio, refletem sempre o estado ao vivo do RabbitMQ/Redis (nada é
armazenado ou simulado). Detalhes de motivação em
[`web-frontend.md`](./web-frontend.md).

### `GET /operacoes/fila` — status da fila de aplicações

```json
{
  "sucesso": true,
  "dados": {
    "aplicacoes": { "nome": "investrenda.aplicacoes.fila", "existe": true, "quantidadeMensagens": 0, "quantidadeConsumidores": 1 },
    "filaMorta":   { "nome": "investrenda.aplicacoes.fila.morta", "existe": true, "quantidadeMensagens": 0, "quantidadeConsumidores": 0 }
  },
  "mensagem": "Status da fila consultado com sucesso",
  "erros": []
}
```

### `GET /operacoes/cache` — status do cache de produtos

```json
{
  "sucesso": true,
  "dados": {
    "listaProdutosCacheada": true,
    "listaProdutosTtlSegundos": 599,
    "itensCacheProduto": [
      { "produtoId": "1adbc6db-79aa-4a0e-b352-0113d4267383", "ttlSegundos": 570 }
    ]
  },
  "mensagem": "Status do cache consultado com sucesso",
  "erros": []
}
```

## Integrações externas (`/integracoes`)

Exemplo de chamada a uma API externa (AwesomeAPI, cotação do dólar) protegida
por feature toggle + retry com backoff exponencial + circuit breaker
(Resilience4j) — ver [`decisions.md`](./decisions.md) para o raciocínio
completo e [`architecture.md`](./architecture.md) para onde isso se encaixa.

### `GET /integracoes/cotacao-dolar` — consulta a cotação atual do dólar

```json
{
  "sucesso": true,
  "dados": {
    "par": "USD/BRL",
    "cotacaoCompra": 5.1066,
    "cotacaoVenda": 5.1073,
    "consultadoEm": "2026-09-09T18:33:42.438187553Z"
  },
  "mensagem": "Cotacao consultada com sucesso",
  "erros": []
}
```

`503 Service Unavailable` no mesmo envelope (`sucesso: false`) quando:
- a integração está desabilitada (feature toggle desligado);
- a simulação de falha está ativa e as 3 tentativas de retry se esgotaram;
- o circuit breaker está aberto (falha rápida, sem nem tentar a chamada).

### `GET /integracoes/status` — estado atual da integração

```json
{
  "sucesso": true,
  "dados": { "habilitado": true, "simulandoFalha": false, "estadoCircuito": "CLOSED" },
  "mensagem": "Status da integracao consultado com sucesso",
  "erros": []
}
```

`estadoCircuito` é `CLOSED` (normal), `OPEN` (falha rápida, sem chamar a API
externa) ou `HALF_OPEN` (testando se pode voltar a fechar).

### `PUT /integracoes/habilitar` — liga/desliga a integração (feature toggle)

```json
{ "ativo": false }
```

Devolve o mesmo formato do `/status` já atualizado. Estado em memória — reseta
para o valor do `application.yml` (`investrenda.integracaoexterna.habilitado`)
a cada restart da aplicação.

### `PUT /integracoes/simular-falha` — força falha na próxima consulta, para testar retry/circuit breaker

```json
{ "ativo": true }
```

Com `ativo: true`, toda chamada a `/integracoes/cotacao-dolar` falha
*antes* de tentar a API externa de verdade — determinístico, não depende de
rede. Útil para observar ao vivo o retry (3 tentativas, 1s/2s/4s) e, com
chamadas repetidas, o circuit breaker abrindo (`GET /actuator/circuitbreakers`,
`GET /actuator/retryevents`).
