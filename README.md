<div align="center">

# invest-renda-api

**Microsserviço REST de investimentos em renda fixa (CDB, LCI, LCA)**

Cadastro de produtos, contas por CPF e aplicações com débito de saldo processado de forma assíncrona.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.9.9-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white)](https://www.rabbitmq.com/)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-retry%20%2B%20circuit%20breaker-4051B5?style=for-the-badge)](https://resilience4j.readme.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)

[![CI](https://github.com/wesleyadiel/invest-renda-api/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/wesleyadiel/invest-renda-api/actions/workflows/ci.yml)
[![Build](https://img.shields.io/badge/build-Maven%20Wrapper-C71A36?style=flat-square&logo=apachemaven&logoColor=white)]()
[![Tests](https://img.shields.io/badge/tests-JUnit%205%20%2B%20Mockito-25A162?style=flat-square&logo=junit5&logoColor=white)]()
[![API Docs](https://img.shields.io/badge/docs-OpenAPI%203%20%2F%20Swagger-85EA2D?style=flat-square&logo=swagger&logoColor=black)]()
[![License](https://img.shields.io/badge/license-privado-lightgrey?style=flat-square)]()

</div>

> **Nota:** todos os endpoints desta API podem ser testados localmente com
> qualquer cliente HTTP de sua preferência (Bruno, Insomnia, Postman, `curl`,
> etc.) — não é necessário nenhum projeto adicional. Se preferir uma
> experiência visual, com formulários prontos e painel ao vivo da fila e do
> cache, use o projeto [`invest-renda-web`](https://github.com/wesleyadiel/invest-renda-web).

---

## Sumário

- [O que a API faz](#o-que-a-api-faz)
- [Stack e frameworks](#stack-e-frameworks)
- [Arquitetura](#arquitetura)
- [Como rodar](#como-rodar)
- [Usando a API](#usando-a-api)
- [Como testar](#como-testar)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Documentação completa](#documentação-completa)

---

## O que a API faz

| Domínio | Funcionalidade |
|---|---|
| **Produtos** | Cadastrar, editar, excluir, listar (resumo) e consultar detalhe de produtos de renda fixa — nome, tipo, emissor, taxa, valor mínimo e prazo. Consultas passam por cache Redis. Exclusão é bloqueada se houver aplicações associadas. |
| **Contas** | Abrir uma conta vinculada a um CPF com saldo inicial, consultar saldo e excluir (bloqueada se houver aplicações associadas). |
| **Aplicações** | Aplicar um valor em um produto. A conta é debitada e a aplicação é efetivada por um worker que escuta uma fila (RabbitMQ), usando os dados vigentes do produto (taxa e prazo) no momento do processamento. |
| **Integrações externas** | Exemplo de chamada a uma API externa (cotação do dólar) protegida por feature toggle, retry com backoff exponencial e circuit breaker (Resilience4j) — inclui rota para simular falha e observar o comportamento. |

---

## Stack e frameworks

<table>
<tr><th align="left">Camada</th><th align="left">Tecnologia</th><th align="left">Uso no projeto</th></tr>

<tr><td><b>Linguagem</b></td>
<td><img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21"/></td>
<td>Linguagem principal, LTS</td></tr>

<tr><td><b>Framework</b></td>
<td><img src="https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot"/></td>
<td>Bootstrap da aplicação, injeção de dependência</td></tr>

<tr><td><b>Web</b></td>
<td><img src="https://img.shields.io/badge/Spring%20Web%20MVC-Tomcat%20embutido-6DB33F?logo=spring&logoColor=white" alt="Spring Web"/></td>
<td>Camada REST (controllers)</td></tr>

<tr><td><b>Persistência</b></td>
<td><img src="https://img.shields.io/badge/Spring%20Data%20JPA-Hibernate-6DB33F?logo=hibernate&logoColor=white" alt="Spring Data JPA"/></td>
<td>Mapeamento objeto-relacional</td></tr>

<tr><td><b>Banco de dados</b></td>
<td><img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL"/></td>
<td>Persistência de produtos, contas e aplicações (container Docker)</td></tr>

<tr><td><b>Migrations</b></td>
<td><img src="https://img.shields.io/badge/Flyway-versionado-CC0200?logo=flyway&logoColor=white" alt="Flyway"/></td>
<td>Versionamento e evolução do schema</td></tr>

<tr><td><b>Cache</b></td>
<td><img src="https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white" alt="Redis"/></td>
<td>Cache de consultas de produtos (Spring Cache abstraction)</td></tr>

<tr><td><b>Mensageria</b></td>
<td><img src="https://img.shields.io/badge/RabbitMQ-3.13-FF6600?logo=rabbitmq&logoColor=white" alt="RabbitMQ"/></td>
<td>Fila de aplicações + dead-letter queue (Spring AMQP)</td></tr>

<tr><td><b>Validação</b></td>
<td><img src="https://img.shields.io/badge/Jakarta%20Bean%20Validation-starter--validation-59666C?logo=jakartaee&logoColor=white" alt="Jakarta Validation"/></td>
<td>Validação de payloads + validador customizado de CPF</td></tr>

<tr><td><b>Documentação de API</b></td>
<td><img src="https://img.shields.io/badge/springdoc--openapi-2.6.0-85EA2D?logo=swagger&logoColor=black" alt="springdoc-openapi"/></td>
<td>Swagger UI em <code>/swagger-ui.html</code></td></tr>

<tr><td><b>Observabilidade</b></td>
<td><img src="https://img.shields.io/badge/Spring%20Boot%20Actuator-health-6DB33F?logo=springboot&logoColor=white" alt="Actuator"/></td>
<td><code>/actuator/health</code>, usado pelo healthcheck do container</td></tr>

<tr><td><b>Rate limit</b></td>
<td><img src="https://img.shields.io/badge/Bucket4j-8.10.1-000000?logo=java&logoColor=white" alt="Bucket4j"/></td>
<td>Token bucket em memória, um bucket por IP</td></tr>

<tr><td><b>Resiliência (chamadas externas)</b></td>
<td><img src="https://img.shields.io/badge/Resilience4j-2.2.0-4051B5" alt="Resilience4j"/></td>
<td>Retry com backoff exponencial + circuit breaker na chamada de cotação (<code>/integracoes</code>)</td></tr>

<tr><td><b>Boilerplate</b></td>
<td><img src="https://img.shields.io/badge/Lombok-getters%2Fsetters%2Fbuilder-A50006?logo=lombok&logoColor=white" alt="Lombok"/></td>
<td>Redução de código repetitivo nas entidades/DTOs</td></tr>

<tr><td><b>Build</b></td>
<td><img src="https://img.shields.io/badge/Maven-Wrapper%20(./mvnw)-C71A36?logo=apachemaven&logoColor=white" alt="Maven"/></td>
<td>Não exige Maven pré-instalado na máquina</td></tr>

<tr><td><b>Testes</b></td>
<td><img src="https://img.shields.io/badge/JUnit%205-Mockito-25A162?logo=junit5&logoColor=white" alt="JUnit 5 + Mockito"/> <img src="https://img.shields.io/badge/AssertJ-fluent%20assertions-6E4C13" alt="AssertJ"/></td>
<td>Unitários (services, controllers) sem infraestrutura</td></tr>

<tr><td><b>Testes de integração</b></td>
<td><img src="https://img.shields.io/badge/Testcontainers-PostgreSQL%20real-2496ED?logo=testcontainers&logoColor=white" alt="Testcontainers"/></td>
<td>Repositório contra Postgres real, isolado via Maven Failsafe</td></tr>

<tr><td><b>Containerização</b></td>
<td><img src="https://img.shields.io/badge/Docker-multi--stage%20build-2496ED?logo=docker&logoColor=white" alt="Docker"/> <img src="https://img.shields.io/badge/Docker%20Compose-depend%C3%AAncias-2496ED?logo=docker&logoColor=white" alt="Docker Compose"/></td>
<td><code>Dockerfile</code> (API) + <code>docker/docker-compose.yml</code> (Postgres, Redis, RabbitMQ)</td></tr>

</table>

Todas essas escolhas foram decisões explícitas — veja o porquê de cada uma em
[`.specs/decisions.md`](.specs/decisions.md).

---

## Arquitetura

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
              │ fila de          │
              │ aplicações + DLQ │
              └────────┬─────────┘
                       │ consome
                       ▼
              ┌─────────────────────────────┐
              │ ConsumidorRequisicaoAplicacao│
              │ (mesmo processo da API)      │
              │ debita saldo, conclui a      │
              │ aplicação                    │
              └─────────────────────────────┘
```

O consumer da fila roda **dentro do mesmo processo/JAR** da API. Detalhes completos do
fluxo, do modelo de domínio e de cada decisão técnica em [`.specs/architecture.md`](.specs/architecture.md).

---

## Como rodar

Pré-requisitos: **Docker**. Para rodar a API fora de container, também **Java 21**
(o Maven em si não precisa estar instalado — o projeto usa o **Maven Wrapper**,
`./mvnw`, que baixa a versão certa sozinho no primeiro uso).

### Opção rápida — tudo via Docker

```bash
./run.sh
```

Sobe o Postgres, o Redis, o RabbitMQ e a própria API (build automático a partir do
`Dockerfile`), mostrando o progresso de cada serviço até tudo ficar saudável. Ao
final, a API está em `http://localhost:8080`.

```bash
./run.sh status   # estado atual de cada serviço
./run.sh logs     # acompanhar os logs da API
./run.sh down     # derrubar tudo
```

### Opção para desenvolvimento — API fora do container

```bash
./run.sh deps     # sobe só Postgres, Redis e RabbitMQ
./mvnw spring-boot:run   # roda a API direto na máquina, com hot reload de IDE
```

Instruções completas, incluindo solução de problemas comuns de porta, em
[`.specs/local-development.md`](.specs/local-development.md).

---

## Usando a API

Com a API no ar:

| Recurso | Endereço |
|---|---|
| Swagger UI (documentação interativa) | `http://localhost:8080/swagger-ui.html` |
| Health check | `http://localhost:8080/actuator/health` |
| RabbitMQ (management UI) | `http://localhost:15672` (`guest` / `guest`) |

Todos os endpoints têm rate limit (120 req/min por cliente, configurável em
`investrenda.limiterequisicoes.*`) — ao exceder, retorna `429` no mesmo formato de
resposta padrão. Detalhes em [`.specs/api-reference.md`](.specs/api-reference.md).

Exemplo de fluxo completo via `curl`:

```bash
# cadastrar um produto
curl -X POST localhost:8080/produtos -H 'Content-Type: application/json' -d '{
  "nome": "CDB Banco Inter 120% CDI", "tipo": "CDB", "emissor": "Banco Inter",
  "classificacaoEmissor": "AA+", "taxaAnual": 13.5, "investimentoMinimo": 100.00, "prazoMeses": 12
}'

# abrir uma conta
curl -X POST localhost:8080/contas -H 'Content-Type: application/json' -d '{
  "cpf": "11144477735", "nomeTitular": "Cliente Teste", "saldoInicial": 1000.00
}'

# aplicar (use o id do produto retornado no primeiro passo)
curl -X POST localhost:8080/aplicacoes -H 'Content-Type: application/json' -d '{
  "cpf": "11144477735", "produtoId": "<PRODUTO_ID>", "valor": 300.00
}'

# acompanhar o status da aplicação (processamento é assincrono)
curl localhost:8080/aplicacoes/<APLICACAO_ID>
```

Referência completa de todos os endpoints (payloads, respostas, códigos de erro) em
[`.specs/api-reference.md`](.specs/api-reference.md).

### Validando visualmente, sem `curl`

Há um front-end simples em Angular, [`invest-renda-web`](../invest-renda-web), que
cobre todos os fluxos acima por uma interface, incluindo um painel que mostra o
estado ao vivo da fila e do cache. Ver o `README.md` daquele projeto para instruções.

---

## Como testar

```bash
./mvnw test      # unitários (services, controllers, validação) - sem Docker, segundos
./mvnw verify    # os de cima + integração (Postgres real via Testcontainers) - precisa de Docker
```

JUnit 5 + Mockito + AssertJ para os unitários (services, controllers via `@WebMvcTest`,
validação de CPF); Testcontainers para o teste de integração (query customizada de
repositório contra Postgres real). Detalhes de cobertura em [`.specs/testing.md`](.specs/testing.md).

---

## Estrutura do projeto

```
invest-renda-api/
├── Dockerfile              # imagem da API para build/implantação
├── run.sh                  # sobe dependências + builda/roda a API (ver "Como rodar")
├── docker/                 # dependências (docker-compose.yml: Postgres, Redis, RabbitMQ)
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/    # Maven Wrapper — "./mvnw" funciona sem Maven pré-instalado
├── .specs/                 # documentação detalhada (arquitetura, decisões, etc.)
└── src/main/java/com/bancointer/investrenda/
    ├── controller/          # camada HTTP
    ├── service/             # regras de negócio e o consumer da fila
    ├── domain/              # entidades JPA
    ├── repository/          # Spring Data JPA
    ├── dto/                 # request/response
    └── config/              # cache, mensageria, CORS, rate limit
```

---

## Documentação completa

O diretório [`.specs`](.specs/README.md) tem a documentação de referência do
projeto:

| Documento | Conteúdo |
|---|---|
| [`architecture.md`](.specs/architecture.md) | Stack, fluxo de ponta a ponta, estrutura de pastas |
| [`domain-model.md`](.specs/domain-model.md) | Entidades, relacionamentos, regras de negócio |
| [`api-reference.md`](.specs/api-reference.md) | Todos os endpoints, payloads e códigos de erro |
| [`caching.md`](.specs/caching.md) | Estratégia de cache Redis e invalidação |
| [`messaging.md`](.specs/messaging.md) | Fila RabbitMQ, DLQ, retry, idempotência |
| [`database.md`](.specs/database.md) | Schema, migrations Flyway |
| [`docker.md`](.specs/docker.md) | Dockerfile, healthcheck, `run.sh` |
| [`testing.md`](.specs/testing.md) | Cobertura de testes unitários e de integração |
| [`web-frontend.md`](.specs/web-frontend.md) | O front-end Angular de validação |
| [`decisions.md`](.specs/decisions.md) | Registro de decisões de arquitetura (o porquê de cada escolha) |
