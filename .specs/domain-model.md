# Modelo de domínio

Três entidades JPA, em `domain/`:

## `Produto` (tabela `produto`)

Representa um produto de renda fixa (CDB, LCI ou LCA) oferecido pela
instituição.

| Campo               | Tipo         | Observação |
|---------------------|--------------|------------|
| `id`                | `UUID`       | gerado |
| `nome`               | `String`     | |
| `tipo`               | `TipoProduto`| enum: `CDB`, `LCI`, `LCA` |
| `emissor`             | `String`     | |
| `classificacaoEmissor` | `String`   | rating do emissor (ex: `AA+`) |
| `taxaAnual`         | `BigDecimal` | taxa anual, em % |
| `investimentoMinimo`  | `BigDecimal` | valor mínimo de aplicação |
| `prazoMeses`     | `Integer`    | prazo até o vencimento, em meses |
| `ativo`             | `boolean`    | produto indisponível para novas aplicações se `false` |
| `aplicacoes`        | `List<Aplicacao>` | `@OneToMany(mappedBy = "produto")`, lado inverso do `@ManyToOne` de `Aplicacao` — `Aplicacao` continua dona da FK; usado pelo relatório `GET /produtos/{id}/relatorio` |
| `criadoEm`/`atualizadoEm` | `Instant` | gerenciados pelo Hibernate (`@CreationTimestamp`/`@UpdateTimestamp`) |

## `Conta` (tabela `conta`)

Conta do cliente, uma por CPF, com saldo disponível para aplicações.

| Campo         | Tipo         | Observação |
|---------------|--------------|------------|
| `id`          | `UUID`       | gerado |
| `cpf`         | `String(11)` | único, só dígitos |
| `nomeTitular`  | `String`     | |
| `saldo`     | `BigDecimal` | saldo disponível |
| `criadoEm`/`atualizadoEm` | `Instant` | |

Métodos de domínio: `debitar(valor)` e `temSaldoSuficiente(valor)` — a
lógica de mexer no saldo vive na própria entidade, não espalhada no service.

## `Aplicacao` (tabela `aplicacao`)

Uma aplicação (compra) de um produto por uma conta.

| Campo            | Tipo               | Observação |
|------------------|--------------------|------------|
| `id`             | `UUID`             | gerado |
| `conta`        | `Conta` (FK)     | |
| `produto`        | `Produto` (FK)     | |
| `valor`         | `BigDecimal`       | valor aplicado |
| `status`         | `StatusAplicacao`| `PENDENTE` → `CONCLUIDA` ou `FALHOU` |
| `motivoFalha`  | `String`           | preenchido só quando `FALHOU` |
| `taxaAplicada`    | `BigDecimal`       | taxa do produto **no momento em que a aplicação foi processada** (não no momento do request) |
| `dataVencimento`   | `LocalDate`        | calculada como `hoje + prazoMeses` do produto, no momento do processamento |
| `criadoEm`/`atualizadoEm` | `Instant`   | |

### Ciclo de vida de uma `Aplicacao`

```
PENDENTE  --(saldo suficiente)-->  CONCLUIDA
   |
   +------(saldo insuficiente)-->  FALHOU
```

Uma aplicação nasce sempre `PENDENTE` (criada pela API) e é finalizada de
forma assíncrona pelo consumer da fila — nunca é criada já como `CONCLUIDA`
ou `FALHOU`. Detalhes do fluxo completo em [`messaging.md`](./messaging.md).

## Regras de negócio importantes

- **CPF é único por conta**: tentar criar uma segunda conta com o mesmo CPF
  retorna `409 Conflict` (`ContaService.criar`).
- **Produto inativo não aceita aplicação**: validado no request
  (`AplicacaoService.solicitarAplicacao`), retorna `422`.
- **Valor abaixo do mínimo do produto**: validado no request, `422`.
- **Saldo insuficiente**: só é possível descobrir isso de forma segura no
  momento do débito (por causa de concorrência entre aplicações da mesma
  conta), então essa checagem acontece no consumer, não no request — a
  aplicação é aceita como `PENDENTE` e vira `FALHOU` de forma assíncrona.
- **Taxa e vencimento aplicados são os vigentes no momento do processamento**,
  não os do momento em que o cliente pediu. Se o produto for editado entre o
  request e o processamento da fila (normalmente milissegundos, mas
  tecnicamente possível), a aplicação usa o dado mais recente.
- **CPF é validado matematicamente** (dígito verificador, não só formato) —
  ver `validation/ValidadorCpf.java`.
