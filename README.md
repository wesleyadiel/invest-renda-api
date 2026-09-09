# invest-renda-api

Microsserviço REST para investimentos em renda fixa (CDB, LCI, LCA) de um
banco digital: cadastro e consulta de produtos, abertura de contas por CPF
com saldo, e aplicação (compra) de investimentos com débito de saldo
processado de forma assíncrona.

## O que a API faz

- **Produtos**: cadastrar, editar, listar (resumo) e consultar detalhe de
  produtos de renda fixa — nome, tipo, emissor, taxa, valor mínimo e prazo.
  Consultas passam por cache Redis.
- **Contas**: abrir uma conta vinculada a um CPF com saldo inicial, e
  consultar saldo.
- **Aplicações**: aplicar um valor em um produto. A conta é debitada e a
  aplicação é efetivada por um worker que escuta uma fila (RabbitMQ),
  usando os dados vigentes do produto (taxa e prazo) no momento do
  processamento.

## Stack

Java 21 · Spring Boot · PostgreSQL (Flyway) · Redis (cache) · RabbitMQ
(fila) · Docker / Docker Compose.

## Como rodar

Pré-requisitos: Docker. Para rodar a API fora de container, também Java 21
(o Maven em si não precisa estar instalado — o projeto usa o **Maven
Wrapper**, `./mvnw`, que baixa a versão certa do Maven sozinho no primeiro
uso).

### Opção rápida — tudo via Docker

```bash
./docker/run.sh
```

Sobe o Postgres, o Redis, o RabbitMQ e a própria API (build automático a
partir do `Dockerfile`), mostrando o progresso de cada serviço até tudo
ficar saudável. Ao final, a API está em `http://localhost:8080`.

Outros comandos úteis:

```bash
./docker/run.sh status   # estado atual de cada serviço
./docker/run.sh logs     # acompanhar os logs da API
./docker/run.sh down     # derrubar tudo
```

### Opção para desenvolvimento — API fora do container

```bash
./docker/run.sh deps     # sobe só Postgres, Redis e RabbitMQ
./mvnw spring-boot:run      # roda a API direto na máquina, com hot reload de IDE
```

Instruções completas, incluindo solução de problemas comuns de porta, em
[`.specs/local-development.md`](.specs/local-development.md).

## Usando a API

Com a API no ar:

- **Swagger UI** (documentação interativa): `http://localhost:8080/swagger-ui.html`
- **Health check**: `http://localhost:8080/actuator/health`
- **RabbitMQ (management UI)**: `http://localhost:15672` (usuário/senha `guest`/`guest`)

Todos os endpoints têm rate limit (120 req/min por cliente, configurável em
`investrenda.ratelimit.*`) — ao exceder, retorna `429` no mesmo formato de
resposta. Detalhes em [`.specs/api-reference.md`](.specs/api-reference.md).

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

Referência completa de todos os endpoints (payloads, respostas, códigos de
erro) em [`.specs/api-reference.md`](.specs/api-reference.md).

### Validando visualmente, sem `curl`

Há um front-end simples em Angular, [`invest-renda-web`](../invest-renda-web),
que cobre todos os fluxos acima por uma interface, incluindo um painel que
mostra o estado ao vivo da fila e do cache. Ver o `README.md` daquele
projeto para instruções.

## Como testar

```bash
./mvnw test      # unitários (services, controllers, validação) - sem Docker, segundos
./mvnw verify    # os de cima + integração (Postgres real via Testcontainers) - precisa de Docker
```

JUnit 5 + Mockito + AssertJ para os unitários (services, controllers via
`@WebMvcTest`, validação de CPF); Testcontainers para o teste de integração
(query customizada de repositório contra Postgres real). Detalhes de
cobertura em [`.specs/testing.md`](.specs/testing.md).

## Estrutura do projeto

```
invest-renda-api/
├── Dockerfile              # imagem da API para build/implantação
├── docker/                 # dependencias (Postgres, Redis, RabbitMQ) + script de execução
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/    # Maven Wrapper - "./mvnw" funciona sem Maven pre-instalado
├── .specs/                 # documentação detalhada (arquitetura, decisões, etc.)
└── src/main/java/com/bancointer/investrenda/
    ├── controller/          # camada HTTP
    ├── service/             # regras de negócio e o consumer da fila
    ├── domain/              # entidades JPA
    ├── repository/          # Spring Data JPA
    ├── dto/                 # request/response
    └── config/              # cache, mensageria, CORS
```

## Documentação completa

O diretório [`.specs`](.specs/README.md) tem a documentação de referência
do projeto: arquitetura, modelo de domínio, cache, mensageria, banco de
dados, testes e o registro de decisões de arquitetura (o porquê de cada
escolha não óbvia).
