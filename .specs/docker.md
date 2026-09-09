# Docker

Duas coisas bem separadas, de propósito:

- **`Dockerfile`** (na raiz do projeto) — a imagem da **nossa própria API**,
  pensada para build/implantação (o artefato que vai rodar em produção).
- **`docker/docker-compose.yml`** — só as **dependências** da API para rodar
  localmente: banco (Postgres), fila (RabbitMQ) e cache (Redis). Não inclui
  a API.
- **`docker/run.sh`** — script que orquestra os dois: sobe as dependências
  via compose e, opcionalmente, builda a imagem a partir do `Dockerfile` da
  raiz e roda a API conectada à mesma rede Docker.

## `Dockerfile` (raiz) — imagem da API

Build multi-estágio:

1. **Estágio `build`** (`maven:3.9.9-eclipse-temurin-21`): copia só o
   `pom.xml` primeiro e roda `dependency:go-offline` antes de copiar o
   `src/` — assim o Docker cacheia a layer de dependências e só baixa tudo
   de novo se o `pom.xml` mudar, não a cada alteração de código. Depois
   builda o jar (`mvn package`, sem rodar os testes — testes rodam antes do
   build/no CI, não durante ele).
2. **Estágio final** (`eclipse-temurin:21-jre-alpine`): só o JRE, não o JDK
   nem o Maven — imagem final bem menor. Roda como usuário não-root
   (`investrenda`). Expõe a porta 8080.

A imagem final não inclui código-fonte, Maven ou o JDK completo — só o jar
e o runtime Java. Build manual, sem o script:

```bash
docker build -t invest-renda-api:local .
```

## `docker/docker-compose.yml` — dependências

Só três serviços: `postgres`, `redis`, `rabbitmq`, cada um com seu próprio
healthcheck. Declara uma rede nomeada explicitamente
(`invest-renda-network`) para que o container da API (subido à parte, via
`docker run`, não via este compose) consiga se conectar a ela de forma
previsível.

Subir só isso (para rodar a API direto na sua máquina por fora do Docker):

```bash
docker compose -f docker/docker-compose.yml up -d
# ou, com o script:
./docker/run.sh deps
```

## `docker/run.sh` — orquestração + painel visual

Subcomandos:

| Comando | O que faz |
|---|---|
| `./docker/run.sh` (ou `up`) | Sobe as dependências, builda a imagem da API a partir do `Dockerfile` da raiz, roda o container da API na mesma rede, acompanha a saúde de tudo com um painel animado e por fim segue os logs da API. |
| `./docker/run.sh deps` | Sobe só as dependências (banco, fila, cache) — para quem vai rodar a API localmente via `mvn spring-boot:run`. |
| `./docker/run.sh status` | Mostra o painel de saúde uma vez (dependências + API) e sai. |
| `./docker/run.sh logs` | Segue os logs do container da API. |
| `./docker/run.sh down [-v]` | Remove o container da API e derruba as dependências (`-v` também apaga o volume do Postgres). |

Como a API é rodada com `docker run` (não faz parte do
`docker/docker-compose.yml`), o script passa manualmente as variáveis de
ambiente que apontam para os outros containers pelo **nome do container**
(`invest-renda-postgres`, `invest-renda-redis`, `invest-renda-rabbitmq`) na
rede `invest-renda-network`, e replica via `--health-cmd`/`--health-*` o
mesmo healthcheck que seria feito por um `HEALTHCHECK` de Dockerfile/compose
— assim o painel de status funciona igual para os quatro containers, mesmo
o da API não sendo gerenciado pelo compose de dependências.

> O `application.yml` continua com `localhost` como default (usado quando a
> API roda fora do Docker) — os nomes de container só entram via variável de
> ambiente passada no `docker run`/`docker-compose`, nunca editando o
> `application.yml`.

Decisões de implementação do script que valem registro:

- **Arrays indexados em paralelo, não array associativo**: `SERVICES`,
  `CONTAINERS`, `LABELS`, `STATUSES` são arrays separados de mesmo tamanho,
  casados por índice — não `declare -A`. Motivo: o `/bin/bash` que vem por
  padrão no macOS é a versão 3.2 (por licenciamento, a Apple não atualiza),
  que não suporta array associativo (recurso do bash 4+). Arrays indexados
  funcionam em qualquer versão.
- **Se um container ficar `unhealthy`, o script já imprime os últimos logs
  dele automaticamente** antes de sair com erro — evita o passo manual de
  "agora roda `docker logs` pra ver o que houve".
- **`Ctrl+C` só derruba o acompanhamento de logs, não os containers** — o
  `trap ... INT` captura o sinal só para sair do `docker logs -f` de forma
  limpa; parar os containers de verdade é sempre uma ação explícita
  (`./docker/run.sh down`), nunca um efeito colateral de interromper o
  script.
