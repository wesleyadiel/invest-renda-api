# Testes

Duas camadas, com propósitos e comandos diferentes:

| | Unitários | Integração |
|---|---|---|
| Comando | `./mvnw test` | `./mvnw verify` |
| Precisa de Docker? | Não | Sim (Testcontainers sobe um Postgres real) |
| Velocidade | Segundos | ~15-25s (subida do container) |
| Convenção de nome | `*Test.java` (Surefire) | `*IT.java` (Failsafe) |

`./mvnw verify` roda as duas camadas (unitários primeiro, depois integração).
`./mvnw test` roda só a rápida. Essa separação é deliberada: quem está
iterando em código não deveria precisar do Docker de pé só para rodar
`./mvnw test` a cada `Ctrl+S`.

## Testes unitários (`./mvnw test`)

Mockito puro (sem subir Spring context, sem infraestrutura real).

| Classe testada | Arquivo de teste | Cobre |
|---|---|---|
| `ProdutoService` | `ProdutoServiceTest` | criação com `ativo` default, produto não encontrado, relatório de aplicações (com/sem aplicações, 404, e inconsistência quando uma aplicação retornada referencia outro produto ou nenhum produto) |
| `ContaService` | `ContaServiceTest` | saldo inicial default/informado, CPF duplicado, conta não encontrada, listagem |
| `AplicacaoService` | `AplicacaoServiceTest` | publicação na fila quando válido, rejeição quando valor abaixo do mínimo |
| `ConsumidorRequisicaoAplicacao` | `ConsumidorRequisicaoAplicacaoTest` | conclusão com débito de saldo, falha por saldo insuficiente (sem debitar), idempotência (mensagem duplicada ignorada) |
| `ValidadorCpf` | `ValidadorCpfTest` | CPF válido, dígito verificador errado, sequências repetidas, tamanho/caracteres inválidos |
| `FiltroLimiteRequisicoes` | `FiltroLimiteRequisicoesTest` | dentro do limite deixa passar, excede vira 429 no envelope padrão, clientes (IPs) diferentes têm buckets independentes, `habilitado: false` desliga tudo, `X-Forwarded-For` tem prioridade sobre `getRemoteAddr()` |

`@WebMvcTest` (MockMvc, sem servidor real, mas com o Bean Validation e o
`ManipuladorGlobalExcecao` reais — só o service é mockado):

| Controller testado | Arquivo de teste | Cobre |
|---|---|---|
| `ProdutoController` | `ProdutoControllerTest` | criar/atualizar/listar/detalhe, validação de payload (400 com `erros`), 404 traduzido pelo handler, relatório de aplicações (200 com/sem aplicações, 404, `id` malformado, 500 quando o service detecta inconsistência de dados) |
| `ContaController` | `ContaControllerTest` | criar/buscar/listar, CPF matematicamente inválido rejeitado antes de chegar no service, 409 (CPF duplicado) |
| `AplicacaoController` | `AplicacaoControllerTest` | aplicar (202 + `Location`), 422 (regra de negócio), buscar/listar |
| `OperacoesController` | `OperacoesControllerTest` | `/operacoes/fila` e `/operacoes/cache` com `AmqpAdmin`/`StringRedisTemplate` mockados diretamente (não tem service próprio) |

## Teste de integração (`./mvnw verify`)

`AplicacaoRepositoryIT` — sobe um Postgres real via Testcontainers,
aplica as migrations Flyway de verdade, e testa as duas queries JPQL
customizadas do projeto que um repositório mockado nunca executa de fato:

- `AplicacaoRepository.buscarTodasOrdenadasPorDataCriacaoDesc()` — confirma
  a ordenação e que o `join fetch` realmente evita
  `LazyInitializationException` ao acessar `conta`/`produto` depois.
- `AplicacaoRepository.buscarPorProdutoIdOrdenadasPorDataCriacaoDesc()` —
  confirma que o filtro por produto não vaza aplicação de outro produto
  (usado pelo relatório) e a ordenação. Também confirma, contra Postgres de
  verdade, que `Produto.aplicacoes` (`@OneToMany(mappedBy = "produto")`)
  enxerga as aplicações salvas do lado inverso do relacionamento.
- `ContaRepository.buscarPorIdParaAtualizacao()` — confirma que o lock
  pessimista compila e executa contra Postgres de verdade (o log do
  Hibernate mostra `for no key update` na query).

**Gotcha de ambiente, se `./mvnw verify` falhar com
`Could not find a valid Docker environment`**: Testcontainers usa uma
biblioteca cliente (`docker-java`) que faz sua própria negociação de versão
da API do Docker, separada da CLI `docker` (que sempre funciona). Em
Docker Desktop muito recente, a versão antiga de API que essa negociação
tenta primeiro pode já ter sido descontinuada pelo daemon (erro HTTP 400).
O projeto já sobrescreve a versão do Testcontainers no `pom.xml`
(`<testcontainers.version>`, por cima do que o Spring Boot 3.3.4 gerencia)
justamente por causa disso — se voltar a acontecer, o primeiro passo é
tentar uma versão mais nova dessa property, não mexer na versão do Docker
Desktop.

## O que ainda não existe

- **Teste de concorrência de verdade** para o lock pessimista (duas
  aplicações simultâneas na mesma conta, threads reais disputando a linha)
  — o `AplicacaoRepositoryIT` confirma que a query roda, não que ela
  bloqueia a segunda transação como esperado. É o próximo passo mais valioso
  se for continuar investindo em testes, porque é o que mais protege contra
  um bug caro (conta ficar negativa).
- Testes de integração para Redis/RabbitMQ reais (hoje só Postgres tem
  Testcontainers) — o comportamento de cache e fila já foi validado
  manualmente de ponta a ponta (ver `.specs/decisions.md`), mas não por
  teste automatizado.
