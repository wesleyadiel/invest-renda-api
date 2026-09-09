# Rodando localmente

Duas formas de rodar o projeto. Use a **Opção A** (Docker completo) se só
quer ver o sistema funcionando ou testar a API. Use a **Opção B** (app fora
do Docker) se for desenvolver/debugar a aplicação de perto — build de código
Java dentro de container é mais lento por iteração do que rodar direto na
sua máquina.

## Pré-requisitos

- Docker (com `docker compose`) — para as duas opções.
- Java 21 — só para a Opção B (o Maven em si não precisa estar
  instalado — use `./mvnw`, o Maven Wrapper, que baixa a versão certa
  sozinho).

---

## Opção A — tudo via Docker (Postgres + Redis + RabbitMQ + a própria API)

```bash
./docker/run.sh
```

Um único comando: sobe as dependências (`docker/docker-compose.yml`),
builda a imagem da API a partir do `Dockerfile` na raiz, roda o container
da API na mesma rede, mostra um painel visual acompanhando cada serviço
ficar saudável e, no final, passa a seguir os logs da API. `Ctrl+C` só sai
do acompanhamento de logs — os containers continuam rodando.

> O painel colorido/animado só aparece corretamente em um terminal de
> verdade (Terminal.app, iTerm2, etc.) — rodar por uma ferramenta que não
> interpreta códigos ANSI mostra os símbolos de controle como texto.

Outros comandos do script:

```bash
./docker/run.sh deps     # sobe só as dependências (ver Opção B abaixo)
./docker/run.sh status   # mostra o painel de saúde uma vez e sai
./docker/run.sh logs     # só segue os logs da API
./docker/run.sh down     # derruba tudo (mantém os dados do Postgres)
./docker/run.sh down -v  # derruba tudo e apaga os dados do Postgres também
```

A API fica em `http://localhost:8080` (ver URLs completas no resumo que o
script imprime ao final). Detalhes de como o Dockerfile e o healthcheck
funcionam em [`docker.md`](./docker.md).

---

## Opção B — infraestrutura no Docker, API rodando direto na sua máquina

Melhor para desenvolvimento ativo (hot reload de IDE, debugger anexado,
etc.).

### 1. Subir só a infraestrutura

```bash
./docker/run.sh deps
# ou diretamente:
docker compose -f docker/docker-compose.yml up -d
```

| Serviço  | Container              | Porta no host | Observação |
|----------|-------------------------|----------------|------------|
| Postgres | `invest-renda-postgres` | **5433** → 5432 | Porta 5432 costuma já estar ocupada por um Postgres local nativo — por isso o mapeamento é 5433. Se sua máquina não tiver esse conflito, pode mudar de volta para 5432 no `docker-compose.yml` **e** no `application.yml` (`spring.datasource.url`). |
| Redis    | `invest-renda-redis`    | 6379 → 6379 | |
| RabbitMQ | `invest-renda-rabbitmq` | 5672 (AMQP) e 15672 (UI de management, usuário/senha `guest`/`guest`) | |

Credenciais do Postgres: banco `invest_renda`, usuário/senha `invest_renda`/`invest_renda`.

Aguarde os três ficarem `healthy`:

```bash
docker inspect -f '{{.State.Health.Status}}' invest-renda-postgres invest-renda-redis invest-renda-rabbitmq
```

### 2. Rodar a aplicação

```bash
./mvnw spring-boot:run
```

Ou empacotar e rodar o jar:

```bash
./mvnw -DskipTests package
java -jar target/invest-renda-api-1.0.0.jar
```

Sobe em `http://localhost:8080` por padrão (`server.port` em
`application.yml`). Se a porta 8080 já estiver em uso por outra coisa na sua
máquina, rode com `--server.port=<outra porta>` sem precisar editar o
arquivo.

Flyway aplica as migrations automaticamente na subida.

### 3. Testar rapidamente

```bash
# criar produto
curl -X POST localhost:8080/produtos -H 'Content-Type: application/json' -d '{
  "nome": "CDB Teste", "tipo": "CDB", "emissor": "Banco X", "classificacaoEmissor": "AA",
  "taxaAnual": 12.5, "investimentoMinimo": 100.00, "prazoMeses": 12
}'

# criar conta
curl -X POST localhost:8080/contas -H 'Content-Type: application/json' -d '{
  "cpf": "11144477735", "nomeTitular": "Cliente Teste", "saldoInicial": 1000.00
}'

# aplicar (use o id do produto retornado acima)
curl -X POST localhost:8080/aplicacoes -H 'Content-Type: application/json' -d '{
  "cpf": "11144477735", "produtoId": "<PRODUTO_ID>", "valor": 300.00
}'
```

Ou use a Swagger UI em `http://localhost:8080/swagger-ui.html`.

### 4. Encerrar

```bash
# só a aplicação: Ctrl+C, ou mate o processo java
# infraestrutura:
docker compose -f docker/docker-compose.yml down          # mantém os dados (volume do Postgres)
docker compose -f docker/docker-compose.yml down -v       # apaga também os dados do Postgres
```

---

## Problemas comuns

- **`role "invest_renda" does not exist`**: geralmente significa que a porta
  5432/5433 mapeada está sendo respondida por outro Postgres (nativo ou de
  outro projeto), não pelo container. Confira com
  `lsof -nP -iTCP:<porta> -sTCP:LISTEN` e ajuste a porta se necessário.
- **`Port 8080 was already in use`**: outro processo seu (ou outro projeto)
  está na porta. Na Opção B, suba com `--server.port=<outra>` em vez de
  matar processos que não sejam seus. Na Opção A (Docker), pare quem estiver
  usando 8080 ou mude o mapeamento de porta (`-p`) do `docker run` da API em
  `docker/run.sh`.
- **`docker/run.sh` trava esperando o `invest-renda-api` ficar saudável**:
  normalmente é a aplicação não conseguindo conectar em `postgres`/`redis`/
  `rabbitmq` (nomes de serviço, não `localhost`, dentro da rede do Compose).
  O próprio script imprime os últimos logs do container automaticamente
  quando detecta `unhealthy` — comece por aí.
