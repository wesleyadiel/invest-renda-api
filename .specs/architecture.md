# Arquitetura

## Stack

| Camada          | Tecnologia                              |
|-----------------|------------------------------------------|
| Linguagem       | Java 21                                   |
| Framework       | Spring Boot 3.3.4                         |
| Web             | Spring Web (MVC, Tomcat embutido)         |
| Persistência    | Spring Data JPA + Hibernate               |
| Banco de dados  | PostgreSQL 16 (container Docker)          |
| Migrations      | Flyway                                    |
| Cache           | Redis 7 (container Docker), via Spring Cache abstraction |
| Mensageria      | RabbitMQ 3.13 (container Docker), via Spring AMQP |
| Validação       | Jakarta Bean Validation (`spring-boot-starter-validation`) |
| Documentação de API | springdoc-openapi (Swagger UI em `/swagger-ui.html`) |
| Observabilidade | Spring Boot Actuator (`/actuator/health`, usado pelo healthcheck do container) |
| Rate limit      | Bucket4j (token bucket, em memória, um bucket por IP) |
| Resiliência (chamadas externas) | Resilience4j (retry com backoff exponencial + circuit breaker) |
| Build           | Maven (via Maven Wrapper, `./mvnw` — não exige Maven pré-instalado) |
| Testes          | JUnit 5 + Mockito + AssertJ (unitários, sem infraestrutura) |
| Containerização | Docker multi-stage build (`Dockerfile` na raiz) + Docker Compose |

Todas essas escolhas foram decisões explícitas — veja o porquê de cada uma em
[`decisions.md`](./decisions.md).

## Visão geral do fluxo

```
                 ┌──────────────────────────┐
Cliente  ──HTTP──▶  invest-renda-api (8080) │
                 │  Spring Boot             │
                 └─────┬──────────┬─────────┘
                       │          │
              ┌────────▼───┐   ┌──▼─────────────┐
              │  PostgreSQL │   │  Redis (cache)  │
              │  (produtos, │   │  produtos:      │
              │  contas,    │   │  lista + detalhe│
              │  aplicações)│   └─────────────────┘
              └─────────────┘
                       │
                       │ publica ao criar aplicação
                       ▼
              ┌─────────────────┐
              │   RabbitMQ       │
              │ applications     │
              │ queue + DLQ      │
              └────────┬─────────┘
                       │ consome
                       ▼
              ┌─────────────────────────┐
              │ ApplicationRequestConsumer│
              │ (mesmo processo da API) │
              │ debita saldo, conclui   │
              │ a aplicação             │
              └─────────────────────────┘
```

O consumer da fila roda **dentro do mesmo processo/JAR** da API (não é um
serviço separado). Isso simplifica a operação para o escopo atual do projeto
— se o volume de aplicações crescer muito, extrair o consumer para um
processo/deploy independente é a evolução natural (escala independente do
lado de escrita).

## Estrutura de pastas

```
invest-renda-api/
├── Dockerfile                   # build multi-estagio da API (Maven -> JRE) - imagem para implantar a API
├── run.sh                       # orquestra tudo via Docker com painel visual de progresso
├── docker/
│   └── docker-compose.yml      # dependencias: Postgres, Redis, RabbitMQ (NAO a API)
├── pom.xml
├── .specs/                     # esta documentação
└── src/
    ├── main/
    │   ├── java/com/bancointer/investrenda/
    │   │   ├── InvestRendaApiApplication.java   # entrypoint + ordem AOP cache/transação (nome em ingles - convencao Spring Boot, evita colidir com a entidade Aplicacao)
    │   │   ├── config/
    │   │   │   ├── ConfiguracaoCacheRedis.java  # serialização JSON + TTL do cache
    │   │   │   ├── ConfiguracaoRabbitMQ.java    # exchange, fila, DLQ, binding
    │   │   │   ├── ConfiguracaoCors.java
    │   │   │   └── ConfiguracaoLimiteRequisicoes.java, FiltroLimiteRequisicoes.java, PropriedadesLimiteRequisicoes.java  # rate limit
    │   │   ├── controller/                      # camada HTTP (REST) - ProdutoController, ContaController, AplicacaoController, OperacoesController, IntegracaoExternaController
    │   │   ├── service/                         # regras de negócio
    │   │   │   ├── ProdutoService.java
    │   │   │   ├── ContaService.java
    │   │   │   ├── AplicacaoService.java        # publica na fila (produtor)
    │   │   │   ├── ConsumidorRequisicaoAplicacao.java  # consome a fila
    │   │   │   ├── ServicoIntegracaoExterna.java  # @Retry + @CircuitBreaker (Resilience4j), feature toggle
    │   │   │   ├── ClienteCotacaoDolar.java       # fala com a AwesomeAPI (cotacao do dolar)
    │   │   │   └── EstadoIntegracaoExterna.java   # toggles em memoria (habilitado / simular falha)
    │   │   ├── domain/                          # entidades JPA (Produto, Conta, Aplicacao)
    │   │   │   └── enums/                       # TipoProduto, StatusAplicacao
    │   │   ├── dto/                             # records de request/response, todos em portugues
    │   │   ├── repository/                      # Spring Data JPA repositories
    │   │   ├── messaging/                       # payload da mensagem da fila
    │   │   ├── exception/                       # exceções de domínio + ManipuladorGlobalExcecao
    │   │   └── validation/                      # @CPF + ValidadorCpf
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/                    # scripts Flyway (V1 em ingles, V2 traduz tudo para portugues)
    └── test/
        └── java/com/bancointer/investrenda/{service,controller,repository,validation,config}/  # testes unitários (Mockito) + integração (Testcontainers)
```

Convenção de pacotes: um pacote por **responsabilidade técnica**
(`controller`, `service`, `repository`, `dto`...), não por feature. Como o
domínio hoje tem só três agregados (produto, conta, aplicação) e poucos
endpoints, separar por feature traria mais indireção do que benefício. Se o
domínio crescer bastante, vale reavaliar para pacotes por feature
(`product/`, `account/`, `application/`).
