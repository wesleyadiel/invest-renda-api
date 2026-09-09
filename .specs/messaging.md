# Mensageria (RabbitMQ) — fluxo de aplicação

Este é o mecanismo assíncrono que efetiva uma aplicação (compra) de
investimento. Requisito de negócio: aplicar deve debitar o saldo da conta e
usar os dados vigentes do produto — e isso acontece através de uma fila e um
consumer, não diretamente no request HTTP.

## Topologia (`config/ConfiguracaoRabbitMQ.java`)

| Recurso | Nome | Tipo |
|---------|------|------|
| Exchange | `investrenda.aplicacoes.exchange` | `DirectExchange` |
| Fila principal | `investrenda.aplicacoes.fila` | durável |
| Routing key | `investrenda.aplicacoes.aplicar` | liga fila ↔ exchange |
| Dead-letter queue | `investrenda.aplicacoes.fila.morta` | durável |

A fila principal é declarada com os argumentos `x-dead-letter-exchange` /
`x-dead-letter-routing-key` apontando para a DLQ — ou seja, o roteamento para
a DLQ é feito pelo próprio RabbitMQ quando uma mensagem é rejeitada
definitivamente, não por código nosso.

Mensagens trafegam como JSON (`Jackson2JsonMessageConverter`), payload
definido em `messaging/MensagemAplicacaoSolicitada.java`:

```json
{
  "aplicacaoId": "...",
  "contaId": "...",
  "produtoId": "...",
  "valor": 300.00
}
```

Propositalmente mínimo — o consumer busca o resto (dados atuais da conta e
do produto) direto no banco, em vez de confiar em uma cópia potencialmente
desatualizada trafegada na mensagem.

## Passo a passo completo

### 1. Produtor — `AplicacaoService.solicitarAplicacao` (síncrono, dentro do `POST /aplicacoes`)

Faz as validações que **não** dependem de concorrência em saldo (conta
existe, produto existe e está ativo, valor ≥ mínimo do produto) e responde
`404`/`422` na hora se algo estiver errado. Se passar:

1. Grava a `Aplicacao` como `PENDENTE` (`saveAndFlush`, para já ter
   `id`/`criadoEm` gerados na resposta).
2. Publica a mensagem na exchange com a routing key.
3. Retorna `202 Accepted`.

O saldo **não é tocado aqui**.

### 2. Consumidor — `ConsumidorRequisicaoAplicacao.processar` (assíncrono, `@RabbitListener`, transação própria)

```java
@RabbitListener(queues = "${investrenda.rabbitmq.fila}")
@Transactional
public void processar(MensagemAplicacaoSolicitada mensagem) { ... }
```

Em ordem:

1. **Busca a `Aplicacao`.** Se `status != PENDENTE`, encerra sem fazer nada.
   Isso protege contra **entrega duplicada** — RabbitMQ garante "pelo menos
   uma entrega", então a mesma mensagem pode chegar mais de uma vez (ex: o
   consumer processa mas cai antes de confirmar o ack). Sem essa checagem, a
   segunda entrega debitaria o saldo de novo pela mesma aplicação.
2. **Busca a `Conta` com lock pessimista** (`SELECT ... FOR UPDATE`, via
   `ContaRepository.buscarPorIdParaAtualizacao`). Isso serializa qualquer
   outra aplicação concorrente na mesma conta — se duas aplicações da mesma
   conta chegarem quase juntas, a segunda espera a primeira terminar antes
   de ler o saldo. Sem isso, as duas poderiam ler o mesmo saldo "antigo" e
   as duas debitarem, deixando a conta negativa.
3. **Busca o `Produto` atual** — não usa nenhum dado vindo na mensagem além
   do `produtoId`, então a taxa e o prazo aplicados são sempre os vigentes
   no momento do processamento.
4. **Confere saldo.** Se insuficiente: marca `FALHOU` com motivo e
   **retorna normalmente** (não lança exceção) — é um resultado de negócio
   esperado, não uma falha técnica, então a mensagem é confirmada (ack) e
   não deve ir para retry/DLQ.
5. Se suficiente: debita a conta, salva, marca a aplicação `CONCLUIDA` com
   `taxaAplicada` e `dataVencimento` calculados a partir do produto.

Tudo isso em uma única transação: ou a conta é debitada **e** a aplicação é
concluída, ou nenhuma das duas coisas acontece (rollback se algo lançar
exceção no meio).

### 3. Retry e DLQ (falhas técnicas de verdade)

Configurado em `application.yml`:

```yaml
spring.rabbitmq.listener.simple.retry:
  enabled: true
  initial-interval: 1000   # 1s
  max-attempts: 3
  multiplier: 2             # 1s, 2s, 4s
```

Se o `processar()` lançar uma exceção (ex: erro de conexão com o banco), o
Spring AMQP tenta de novo automaticamente até 3 vezes com backoff crescente.
Se ainda assim falhar, a mensagem é rejeitada sem *requeue* — e por causa da
configuração de dead-lettering na fila, ela é automaticamente roteada para
`investrenda.aplicacoes.fila.morta`, onde fica visível para investigação
manual em vez de se perder ou de ficar tentando para sempre.

**Importante**: saldo insuficiente **não** passa por esse mecanismo (ver
passo 4 acima) — só bugs/infra quebrada geram retry e, eventualmente, DLQ.

## Por que fila em vez de debitar direto no request

- **Resposta rápida ao cliente**: `POST /aplicacoes` não fica esperando
  lock de conta nem processamento — responde `202` e o cliente consulta o
  status depois.
- **Concorrência mais fácil de raciocinar**: o débito de saldo fica
  centralizado em um único caminho (o consumer), que já usa lock por conta,
  em vez de N requisições HTTP concorrentes cada uma tentando debitar a
  mesma linha do banco.
- **Resiliência**: se o Postgres cair por um instante bem na hora de uma
  aplicação, a mensagem continua na fila e é reprocessada quando o serviço
  voltar — o cliente não perde a solicitação nem precisa refazer o request.

## Verificando o estado das filas manualmente

Com os containers rodando:

```bash
docker exec invest-renda-rabbitmq rabbitmqctl list_queues name messages messages_ready messages_unacknowledged
```

Ou pela UI de management: `http://localhost:15672` (usuário/senha `guest`/`guest`).

Ou pelo próprio endpoint da API: `GET /operacoes/fila` (ver
[`api-reference.md`](./api-reference.md)).
