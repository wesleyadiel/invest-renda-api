#!/usr/bin/env bash
#
# Sobe (ou derruba) as dependencias da invest-renda-api (Postgres, Redis,
# RabbitMQ - definidas em docker/docker-compose.yml) e, opcionalmente,
# builda e roda a propria API a partir do Dockerfile na raiz do projeto,
# conectada na mesma rede. Mostra um painel visual com o progresso de cada
# etapa.
#
# Uso:
#   ./run.sh              sobe as dependencias + builda/roda a API, acompanha health e segue os logs
#   ./run.sh deps         sobe só as dependencias (banco, fila, cache) - útil se for rodar a API
#                          direto na sua máquina via "mvn spring-boot:run"
#   ./run.sh status       só mostra o painel de saúde uma vez e sai
#   ./run.sh logs         só segue os logs da API (sem rebuildar/subir nada)
#   ./run.sh down         derruba tudo (dependências + API), mantém os dados do Postgres
#   ./run.sh down -v      derruba tudo e apaga os dados do Postgres também

set -euo pipefail

# ---------------------------------------------------------------------------
# Setup
# ---------------------------------------------------------------------------

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

DEPS_COMPOSE="docker/docker-compose.yml"
NETWORK="invest-renda-network"
APP_IMAGE="invest-renda-api:local"
APP_CONTAINER="invest-renda-api"

COMMAND="${1:-up}"

if [ -t 1 ]; then
    BOLD=$'\033[1m'; DIM=$'\033[2m'; RESET=$'\033[0m'
    RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[0;33m'
    BLUE=$'\033[0;34m'; CYAN=$'\033[0;36m'
else
    BOLD=""; DIM=""; RESET=""; RED=""; GREEN=""; YELLOW=""; BLUE=""; CYAN=""
fi

FRAMES=(⠋ ⠙ ⠹ ⠸ ⠼ ⠴ ⠦ ⠧ ⠇ ⠏)

# ---------------------------------------------------------------------------
# Helpers visuais
# ---------------------------------------------------------------------------

banner() {
    printf '\n%s%s┌─────────────────────────────────────────────────┐%s\n' "$BOLD" "$CYAN" "$RESET"
    printf '%s%s│  %-47s│%s\n' "$BOLD" "$CYAN" "invest-renda-api - run.sh" "$RESET"
    printf '%s%s└─────────────────────────────────────────────────┘%s\n\n' "$BOLD" "$CYAN" "$RESET"
}

step() { printf '%s%s▶ %s%s\n' "$BOLD" "$BLUE" "$1" "$RESET"; }
ok()   { printf '  %s✔%s %s\n' "$GREEN" "$RESET" "$1"; }
fail() { printf '  %s✖%s %s\n' "$RED" "$RESET" "$1"; }
warn() { printf '  %s!%s %s\n' "$YELLOW" "$RESET" "$1"; }

# ---------------------------------------------------------------------------
# Preflight: garante que o Docker Desktop esta rodando
# ---------------------------------------------------------------------------

ensure_docker_running() {
    step "Verificando o Docker"
    if docker info >/dev/null 2>&1; then
        ok "Docker já está em execução"
        return
    fi

    warn "Docker não está rodando, tentando iniciar..."
    if [[ "$(uname -s)" == "Darwin" ]]; then
        open -a Docker >/dev/null 2>&1 || true
    fi

    local i=0 frame_idx=0
    while ! docker info >/dev/null 2>&1; do
        i=$((i + 1))
        frame_idx=$(((frame_idx + 1) % ${#FRAMES[@]}))
        printf '\r  %s%s%s aguardando o Docker subir... (%ss)' "$CYAN" "${FRAMES[$frame_idx]}" "$RESET" "$i"
        if [ "$i" -ge 60 ]; then
            printf '\n'
            fail "Docker não iniciou em 60s. Abra o Docker Desktop manualmente e rode o script de novo."
            exit 1
        fi
        sleep 1
    done
    printf '\r\033[K'
    ok "Docker está em execução"
}

# ---------------------------------------------------------------------------
# Painel de saude dos servicos
# ---------------------------------------------------------------------------
# Cada chamada de "up" decide se o container da API entra ou nao no painel
# (não entra em "deps"), preenchendo esses arrays antes de chamar
# wait_for_health.

SERVICES=()
CONTAINERS=()
LABELS=()
STATUSES=()

refresh_statuses() {
    local i st
    for i in "${!SERVICES[@]}"; do
        st="$(docker inspect -f '{{.State.Health.Status}}' "${CONTAINERS[$i]}" 2>/dev/null || echo "unknown")"
        STATUSES[$i]="$st"
    done
}

all_healthy() {
    local s
    for s in "${STATUSES[@]}"; do [ "$s" = "healthy" ] || return 1; done
    return 0
}

any_unhealthy() {
    local s
    for s in "${STATUSES[@]}"; do [ "$s" = "unhealthy" ] && return 0; done
    return 1
}

render_dashboard() {
    local frame="$1" first_render="$2" i label st icon text

    if [ "$first_render" != "true" ]; then
        printf '\033[%dA' "${#SERVICES[@]}"
    fi

    for i in "${!SERVICES[@]}"; do
        label="${LABELS[$i]}"
        st="${STATUSES[$i]}"
        case "$st" in
            healthy)   icon="${GREEN}✔${RESET}"; text="${GREEN}saudável${RESET}" ;;
            unhealthy) icon="${RED}✖${RESET}"; text="${RED}com problema${RESET}" ;;
            unknown)   icon="${DIM}·${RESET}"; text="${DIM}container não encontrado${RESET}" ;;
            *)         icon="${CYAN}${frame}${RESET}"; text="${DIM}iniciando...${RESET}" ;;
        esac
        printf '  %s %-18s %s\033[K\n' "$icon" "$label" "$text"
    done
}

wait_for_health() {
    step "Aguardando os serviços ficarem saudáveis"
    echo

    local first_render="true" frame_idx=0 elapsed=0 timeout=180

    render_dashboard "${FRAMES[0]}" "true"
    first_render="false"

    while true; do
        refresh_statuses

        if any_unhealthy; then
            frame_idx=$(((frame_idx + 1) % ${#FRAMES[@]}))
            render_dashboard "${FRAMES[$frame_idx]}" "$first_render"
            echo
            fail "Pelo menos um serviço ficou 'unhealthy'. Últimas linhas de log de cada container com problema:"
            local i
            for i in "${!SERVICES[@]}"; do
                if [ "${STATUSES[$i]}" = "unhealthy" ]; then
                    printf '\n  %s--- %s ---%s\n' "$DIM" "${LABELS[$i]}" "$RESET"
                    docker logs --tail 20 "${CONTAINERS[$i]}" 2>&1 | sed 's/^/  /'
                fi
            done
            exit 1
        fi

        if all_healthy; then
            frame_idx=$(((frame_idx + 1) % ${#FRAMES[@]}))
            render_dashboard "${FRAMES[$frame_idx]}" "$first_render"
            break
        fi

        frame_idx=$(((frame_idx + 1) % ${#FRAMES[@]}))
        render_dashboard "${FRAMES[$frame_idx]}" "$first_render"

        elapsed=$((elapsed + 1))
        if [ "$elapsed" -ge "$timeout" ]; then
            echo
            fail "Timeout de ${timeout}s esperando os serviços ficarem saudáveis."
            exit 1
        fi
        sleep 1
    done

    echo
    ok "Todos os serviços estão saudáveis"
}

# ---------------------------------------------------------------------------
# Dependencias (banco, fila, cache)
# ---------------------------------------------------------------------------

start_deps() {
    step "Subindo as dependências (Postgres, Redis, RabbitMQ)"
    docker compose -f "$DEPS_COMPOSE" up -d
    ok "Dependências no ar"
    echo

    SERVICES=(postgres redis rabbitmq)
    CONTAINERS=(invest-renda-postgres invest-renda-redis invest-renda-rabbitmq)
    LABELS=("Postgres" "Redis" "RabbitMQ")
    STATUSES=(pending pending pending)
    wait_for_health
}

# ---------------------------------------------------------------------------
# API (Dockerfile na raiz do projeto)
# ---------------------------------------------------------------------------

build_and_run_app() {
    step "Construindo a imagem da API (Dockerfile na raiz)"
    docker build -t "$APP_IMAGE" -f Dockerfile .
    ok "Imagem construída"
    echo

    step "Subindo o container da API"
    docker rm -f "$APP_CONTAINER" >/dev/null 2>&1 || true
    docker run -d \
        --name "$APP_CONTAINER" \
        --network "$NETWORK" \
        -p 8080:8080 \
        -e SPRING_DATASOURCE_URL="jdbc:postgresql://invest-renda-postgres:5432/invest_renda" \
        -e SPRING_DATASOURCE_USERNAME="invest_renda" \
        -e SPRING_DATASOURCE_PASSWORD="invest_renda" \
        -e SPRING_DATA_REDIS_HOST="invest-renda-redis" \
        -e SPRING_RABBITMQ_HOST="invest-renda-rabbitmq" \
        --health-cmd="wget --spider -q http://localhost:8080/actuator/health || exit 1" \
        --health-interval=5s \
        --health-timeout=5s \
        --health-retries=20 \
        --health-start-period=30s \
        "$APP_IMAGE" >/dev/null
    ok "Container da API no ar"
    echo

    SERVICES=(app)
    CONTAINERS=("$APP_CONTAINER")
    LABELS=("invest-renda-api")
    STATUSES=(pending)
    wait_for_health
}

summary_panel() {
    printf '\n%s%s  ✔ Tudo no ar%s\n\n' "$BOLD" "$GREEN" "$RESET"
    printf '  %-18s %s%s%s\n' "API"             "$CYAN" "http://localhost:8080" "$RESET"
    printf '  %-18s %s%s%s\n' "Swagger UI"      "$CYAN" "http://localhost:8080/swagger-ui.html" "$RESET"
    printf '  %-18s %s%s%s\n' "Health"          "$CYAN" "http://localhost:8080/actuator/health" "$RESET"
    printf '  %-18s %s%s%s  %s(guest/guest)%s\n' "RabbitMQ (mgmt)" "$CYAN" "http://localhost:15672" "$RESET" "$DIM" "$RESET"
    printf '  %-18s %s%s%s  %s(db=invest_renda)%s\n' "Postgres" "$CYAN" "localhost:5433" "$RESET" "$DIM" "$RESET"
    printf '\n'
}

# ---------------------------------------------------------------------------
# Comandos
# ---------------------------------------------------------------------------

cmd_up() {
    banner
    ensure_docker_running
    start_deps
    build_and_run_app
    summary_panel

    step "Acompanhando os logs da API (Ctrl+C para sair — os containers continuam rodando)"
    echo
    trap 'echo; ok "Saindo do acompanhamento de logs. Os containers continuam rodando."; exit 0' INT
    docker logs -f "$APP_CONTAINER"
}

cmd_deps() {
    banner
    ensure_docker_running
    start_deps
    echo
    ok "Dependências prontas. Rode a API direto na sua máquina, ex.: mvn spring-boot:run"
}

cmd_status() {
    banner
    SERVICES=(postgres redis rabbitmq app)
    CONTAINERS=(invest-renda-postgres invest-renda-redis invest-renda-rabbitmq "$APP_CONTAINER")
    LABELS=("Postgres" "Redis" "RabbitMQ" "invest-renda-api")
    STATUSES=(pending pending pending pending)
    refresh_statuses
    render_dashboard "${FRAMES[0]}" "true"
    echo
}

cmd_logs() {
    banner
    step "Acompanhando os logs da API (Ctrl+C para sair)"
    echo
    docker logs -f "$APP_CONTAINER"
}

cmd_down() {
    banner
    step "Derrubando o container da API"
    docker rm -f "$APP_CONTAINER" >/dev/null 2>&1 || true
    ok "Container da API removido"
    echo

    step "Derrubando as dependências"
    docker compose -f "$DEPS_COMPOSE" down "${@:2}"
    ok "Dependências derrubadas"
}

case "$COMMAND" in
    up) cmd_up ;;
    deps) cmd_deps ;;
    status) cmd_status ;;
    logs) cmd_logs ;;
    down) cmd_down "$@" ;;
    *)
        echo "Uso: $0 [up|deps|status|logs|down [-v]]"
        exit 1
        ;;
esac
