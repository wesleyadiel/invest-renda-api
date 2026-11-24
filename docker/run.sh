#!/usr/bin/env bash
#
# Sobe (ou derruba) só as dependencias da invest-renda-api - Postgres, Redis
# e RabbitMQ, definidas em docker-compose.yml nesta mesma pasta. Não builda
# nem roda a API - a ideia é rodar a API à parte (ex.: via IntelliJ, para
# debugar) apontando para esses serviços. Mostra um painel visual com o
# progresso de cada etapa.
#
# Uso:
#   ./run.sh          sobe as dependencias e acompanha até ficarem saudáveis
#   ./run.sh status   só mostra o painel de saúde uma vez e sai
#   ./run.sh logs     segue os logs das dependencias
#   ./run.sh down     derruba as dependencias, mantém os dados do Postgres
#   ./run.sh down -v  derruba as dependencias e apaga os dados do Postgres também

set -euo pipefail

# ---------------------------------------------------------------------------
# Setup
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

DEPS_COMPOSE="docker-compose.yml"

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
    printf '%s%s│  %-47s│%s\n' "$BOLD" "$CYAN" "invest-renda-api/docker - run.sh" "$RESET"
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

SERVICES=(postgres redis rabbitmq)
CONTAINERS=(invest-renda-postgres invest-renda-redis invest-renda-rabbitmq)
LABELS=("Postgres" "Redis" "RabbitMQ")
STATUSES=(pending pending pending)

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

summary_panel() {
    printf '\n%s%s  ✔ Dependências no ar%s\n\n' "$BOLD" "$GREEN" "$RESET"
    printf '  %-18s %s%s%s  %s(db=invest_renda, user=invest_renda)%s\n' "Postgres" "$CYAN" "localhost:5433" "$RESET" "$DIM" "$RESET"
    printf '  %-18s %s%s%s\n' "Redis"           "$CYAN" "localhost:6379" "$RESET"
    printf '  %-18s %s%s%s\n' "RabbitMQ (AMQP)" "$CYAN" "localhost:5672" "$RESET"
    printf '  %-18s %s%s%s  %s(guest/guest)%s\n' "RabbitMQ (mgmt)" "$CYAN" "http://localhost:15672" "$RESET" "$DIM" "$RESET"
    printf '\n  %sAgora é só rodar/debugar a invest-renda-api pela IDE (IntelliJ) apontando%s\n' "$DIM" "$RESET"
    printf '  %spara esses serviços em localhost.%s\n' "$DIM" "$RESET"
    printf '\n'
}

# ---------------------------------------------------------------------------
# Comandos
# ---------------------------------------------------------------------------

cmd_up() {
    banner
    ensure_docker_running

    step "Subindo as dependências (Postgres, Redis, RabbitMQ)"
    docker compose -f "$DEPS_COMPOSE" up -d
    ok "Dependências no ar"
    echo

    wait_for_health
    summary_panel
}

cmd_status() {
    banner
    refresh_statuses
    render_dashboard "${FRAMES[0]}" "true"
    echo
}

cmd_logs() {
    banner
    step "Acompanhando os logs das dependências (Ctrl+C para sair)"
    echo
    docker compose -f "$DEPS_COMPOSE" logs -f
}

cmd_down() {
    banner
    step "Derrubando as dependências"
    docker compose -f "$DEPS_COMPOSE" down "${@:2}"
    ok "Dependências derrubadas"
}

case "$COMMAND" in
    up) cmd_up ;;
    status) cmd_status ;;
    logs) cmd_logs ;;
    down) cmd_down "$@" ;;
    *)
        echo "Uso: $0 [up|status|logs|down [-v]]"
        exit 1
        ;;
esac
