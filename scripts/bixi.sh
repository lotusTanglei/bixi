#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ENV_FILE="${BIXI_ENV_FILE:-${ROOT}/.env}"
COMPOSE_FILE="${ROOT}/compose.yaml"

info() {
    printf '[bixi] %s\n' "$*"
}

fail() {
    printf '[bixi] ERROR: %s\n' "$*" >&2
    exit 1
}

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

random_hex() {
    if command_exists openssl; then
        openssl rand -hex "${1:-24}"
    else
        date '+%s%N' | shasum -a 256 | awk '{print $1}'
    fi
}

replace_env() {
    key="$1"
    value="$2"
    temp_file="${ENV_FILE}.tmp"
    awk -v key="${key}" -v value="${value}" '
        index($0, key "=") == 1 { print key "=" value; next }
        { print }
    ' "${ENV_FILE}" >"${temp_file}"
    mv "${temp_file}" "${ENV_FILE}"
}

append_env_if_missing() {
    key="$1"
    value="$2"
    if ! grep -q "^${key}=" "${ENV_FILE}"; then
        printf '\n%s=%s\n' "${key}" "${value}" >>"${ENV_FILE}"
    fi
}

upgrade_env() {
    append_env_if_missing TENANT_DEFAULT_PASSWORD "$(random_hex 24)"
    append_env_if_missing DRUID_USERNAME bixi
    append_env_if_missing DRUID_PASSWORD "$(random_hex 24)"
    append_env_if_missing MINIO_ENDPOINT http://127.0.0.1:9800
    append_env_if_missing MINIO_ACCESS_KEY "$(random_hex 24)"
    append_env_if_missing MINIO_SECRET_KEY "$(random_hex 24)"
    append_env_if_missing WORKFLOW_CLUSTER_ENABLED false
    append_env_if_missing WORKFLOW_ASYNC_JOB_LOCK_TIME PT30S
    append_env_if_missing WORKFLOW_TIMER_LOCK_TIME PT30S
    append_env_if_missing WORKFLOW_RESET_EXPIRED_JOBS_INTERVAL PT5S
    append_env_if_missing WORKFLOW_DEFAULT_ASYNC_JOB_ACQUIRE_WAIT_TIME PT1S
    append_env_if_missing WORKFLOW_DEFAULT_TIMER_JOB_ACQUIRE_WAIT_TIME PT1S
    append_env_if_missing WORKFLOW_MAX_ASYNC_JOBS_DUE_PER_ACQUISITION 4
    append_env_if_missing WORKFLOW_MAX_TIMER_JOBS_PER_ACQUISITION 4
    append_env_if_missing WORKFLOW_RESET_EXPIRED_JOBS_PAGE_SIZE 100
    append_env_if_missing WORKFLOW_RESET_EXPIRED_JOB_ENABLED true
    append_env_if_missing WORKFLOW_UNLOCK_OWNED_JOBS true
}

init_env() {
    if [ -f "${ENV_FILE}" ]; then
        upgrade_env
        info '.env already exists; added only missing runtime keys'
        return
    fi

    umask 077
    cp "${ROOT}/.env.example" "${ENV_FILE}"
    for key in MYSQL_PASSWORD MYSQL_ROOT_PASSWORD REDIS_PASSWORD RABBITMQ_PASSWORD \
        BIXI_ENCODE_KEY JASYPT_ENCRYPTOR_PASSWORD OAUTH_PASSWORD_CLIENT_SECRET \
        OAUTH_MOBILE_CLIENT_SECRET OAUTH_INTERNAL_SEED TENANT_DEFAULT_PASSWORD \
        DRUID_PASSWORD MINIO_ACCESS_KEY MINIO_SECRET_KEY ADMIN_PASSWORD; do
        replace_env "${key}" "$(random_hex 24)"
    done
    info 'created .env with random local credentials'
}

load_env() {
    init_env
    set -a
    # shellcheck disable=SC1090
    . "${ENV_FILE}"
    set +a
}

require_docker() {
    command_exists docker || fail 'Docker is not installed'
    docker compose version >/dev/null 2>&1 || fail 'Docker Compose v2 is not available'
    docker info >/dev/null 2>&1 || fail 'Docker daemon is not running'
}

ensure_image() {
    image="$1"
    if docker image inspect "${image}" >/dev/null 2>&1; then
        return
    fi

    attempt=1
    while [ "${attempt}" -le 5 ]; do
        info "pulling ${image} (attempt ${attempt}/5)"
        if docker pull "${image}"; then
            return
        fi
        sleep $((attempt * 5))
        attempt=$((attempt + 1))
    done
    fail "unable to pull ${image} after 5 attempts"
}

select_registry() {
    load_env
    if [ -n "${BIXI_REGISTRY_MIRROR:-}" ] && [ -n "${BIXI_THIRD_PARTY_REGISTRY_MIRROR:-}" ]; then
        return
    fi
    if curl -sS -I --max-time 5 https://registry-1.docker.io/v2/ >/dev/null 2>&1; then
        return
    fi

    if [ -z "${BIXI_REGISTRY_MIRROR:-}" ] \
        && curl -sS -I --max-time 5 https://public.ecr.aws/v2/ >/dev/null 2>&1; then
        replace_env BIXI_REGISTRY_MIRROR public.ecr.aws/docker/
        BIXI_REGISTRY_MIRROR=public.ecr.aws/docker/
        export BIXI_REGISTRY_MIRROR
        info 'Docker Hub is unavailable; using Public ECR for official images'
    fi

    for mirror in nacos-registry.cn-hangzhou.cr.aliyuncs.com/ docker.m.daocloud.io/ docker.1ms.run/; do
        if [ -n "${BIXI_THIRD_PARTY_REGISTRY_MIRROR:-}" ]; then
            break
        fi
        if curl -sS -I --max-time 5 "https://${mirror}v2/" >/dev/null 2>&1; then
            replace_env BIXI_THIRD_PARTY_REGISTRY_MIRROR "${mirror}"
            BIXI_THIRD_PARTY_REGISTRY_MIRROR="${mirror}"
            export BIXI_THIRD_PARTY_REGISTRY_MIRROR
            info "Using ${mirror} for third-party images"
        fi
    done
    [ -n "${BIXI_REGISTRY_MIRROR:-}" ] \
        || fail 'Docker Hub is unavailable and no official-image mirror is reachable'
    [ -n "${BIXI_THIRD_PARTY_REGISTRY_MIRROR:-}" ] \
        || fail 'Docker Hub is unavailable and no third-party mirror is reachable'
}

prepare_images() {
    mode="$1"
    prefix="${BIXI_REGISTRY_MIRROR:-}"
    third_party_prefix="${BIXI_THIRD_PARTY_REGISTRY_MIRROR:-}"
    ensure_image "${prefix}library/mysql:8.4.3"
    ensure_image "${prefix}library/redis:7.4.2-alpine"
    ensure_image "${prefix}library/rabbitmq:4.0.5-management-alpine"
    ensure_image "${prefix}library/maven:3.9.9-eclipse-temurin-17"
    ensure_image "${prefix}library/eclipse-temurin:17.0.13_11-jre-jammy"
    ensure_image "${prefix}library/node:20.18.1-alpine3.20"
    ensure_image "${prefix}library/nginx:1.27.3-alpine3.20"
    if [ "${mode}" = 'cloud' ]; then
        ensure_image "${third_party_prefix}nacos/nacos-server:v2.4.3"
    fi
}

prepare_admin_hash() {
    load_env
    if [ "${ADMIN_PASSWORD_BCRYPT_B64}" != 'GENERATE_ON_FIRST_RUN' ]; then
        return
    fi
    require_docker
    info 'generating the administrator bcrypt hash'
    if command_exists htpasswd; then
        hash="$(htpasswd -bnBC 10 '' "${ADMIN_PASSWORD}" | tr -d ':\n')"
    else
        hash="$(docker run --rm httpd:2.4.62-alpine htpasswd -bnBC 10 '' "${ADMIN_PASSWORD}" | tr -d ':\n')"
    fi
    encoded="$(printf '%s' "${hash}" | base64 | tr -d '\n')"
    replace_env ADMIN_PASSWORD_BCRYPT_B64 "${encoded}"
}

compose() {
    if [ "${WORKFLOW_CLUSTER_ENABLED:-false}" = 'true' ]; then
        docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" \
            -f "${ROOT}/compose.workflow-cluster.yaml" "$@"
    else
        docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
    fi
}

compose_owns_port() {
    port="$1"
    compose --profile cloud --profile single --profile workflow ps --format json 2>/dev/null \
        | grep -F "\"PublishedPort\":${port}" >/dev/null
}

docker_uses_port() {
    port="$1"
    docker ps --format '{{.Ports}}' \
        | grep -E "(^|, | )(127\\.0\\.0\\.1|0\\.0\\.0\\.0|\\[::\\])?:?${port}->" >/dev/null
}

host_uses_port() {
    port="$1"
    if command_exists lsof; then
        lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
    elif command_exists ss; then
        ss -ltn | grep -E "[.:]${port}[[:space:]]" >/dev/null
    else
        return 1
    fi
}

check_port() {
    name="$1"
    port="$2"
    case "${port}" in
        ''|*[!0-9]*) fail "${name} must be a numeric TCP port; found ${port}" ;;
    esac
    [ "${port}" -ge 1 ] && [ "${port}" -le 65535 ] \
        || fail "${name} must be between 1 and 65535; found ${port}"
    if compose_owns_port "${port}"; then
        return
    fi
    if docker_uses_port "${port}" || host_uses_port "${port}"; then
        fail "${name} port ${port} is already in use by another process or Compose project"
    fi
}

check_ports() {
    mode="$1"
    check_port BIXI_HTTP_PORT "${BIXI_HTTP_PORT}"
    check_port MYSQL_PORT "${MYSQL_PORT}"
    check_port REDIS_PORT "${REDIS_PORT}"
    check_port RABBITMQ_PORT "${RABBITMQ_PORT}"
    check_port RABBITMQ_MANAGEMENT_PORT "${RABBITMQ_MANAGEMENT_PORT}"
    if [ "${mode}" = 'single' ]; then
        check_port SINGLE_PORT "${SINGLE_PORT}"
    else
        check_port GATEWAY_PORT "${GATEWAY_PORT}"
        check_port NACOS_PORT "${NACOS_PORT}"
    fi
}

check_version() {
    command_name="$1"
    minimum="$2"
    actual="$3"
    major="$(printf '%s' "${actual}" | sed -E 's/[^0-9]*([0-9]+).*/\1/')"
    [ "${major}" -ge "${minimum}" ] 2>/dev/null \
        || fail "${command_name} ${minimum}+ is required; found ${actual}"
}

doctor() {
    mode="${1:-cloud}"
    load_env
    require_docker
    prepare_admin_hash
    compose --profile cloud --profile single --profile workflow config --quiet
    check_ports "${mode}"

    for file in \
        bixi-project-documents/sql/01_init_all_tables.sql \
        bixi-project-documents/sql/02_add_constraints.sql \
        bixi-project-documents/sql/03_add_indexes.sql \
        bixi-project-documents/sql/04_init_data.sql; do
        [ -s "${ROOT}/${file}" ] || fail "missing database asset: ${file}"
    done

    if [ "${mode}" = 'dev' ]; then
        command_exists java || fail 'Java 17+ is not installed'
        command_exists mvn || fail 'Maven 3.8+ is not installed'
        command_exists node || fail 'Node.js 18+ is not installed'
        command_exists npm || fail 'npm 8+ is not installed'
        check_version Java 17 "$(java -version 2>&1 | head -n 1)"
        check_version Maven 3 "$(mvn -version 2>&1 | head -n 1)"
        check_version Node 18 "$(node --version)"
        check_version npm 8 "$(npm --version)"
    fi

    info "doctor passed (${mode})"
}

wait_for_url() {
    name="$1"
    url="$2"
    attempts="${3:-120}"
    count=1
    while [ "${count}" -le "${attempts}" ]; do
        if curl -fsS "${url}" >/dev/null 2>&1; then
            info "${name} is ready"
            return
        fi
        sleep 5
        count=$((count + 1))
    done
    compose ps
    compose logs --tail=120
    fail "${name} did not become ready: ${url}"
}

show_access() {
    load_env
    info "URL: http://localhost:${BIXI_HTTP_PORT}"
    info "username: ${ADMIN_USERNAME}"
    info "password: ${ADMIN_PASSWORD}"
}

start_cloud() {
    doctor cloud
    load_env
    select_registry
    prepare_images cloud
    compose --profile single stop frontend-single single >/dev/null 2>&1 || true
    workflow_services=""
    workflow_profiles="--profile workflow"
    if [ "${WORKFLOW_ENABLED:-false}" = "true" ]; then
        if [ "${WORKFLOW_CLUSTER_ENABLED:-false}" = "true" ]; then
            workflow_services="workflow-a workflow-b"
            workflow_profiles="--profile workflow-cluster"
            info 'starting two Workflow replicas with distinct lock owners'
        else
            workflow_services="workflow"
        fi
    else
        compose --profile cloud --profile workflow --profile workflow-cluster \
            stop workflow workflow-a workflow-b >/dev/null 2>&1 || true
    fi
    for service in gateway upms auth frontend-cloud ${workflow_services}; do
        compose --profile cloud build "${service}"
    done
    compose --profile cloud up --detach --no-build \
        mysql redis rabbitmq nacos nacos-config gateway upms auth frontend-cloud
    if [ -n "${workflow_services}" ]; then
        compose --profile cloud ${workflow_profiles} up --detach --no-build --wait ${workflow_services}
    fi
    wait_for_url gateway "http://localhost:${GATEWAY_PORT}/actuator/health"
    wait_for_url auth "http://localhost:${GATEWAY_PORT}/auth/actuator/health"
    wait_for_url upms "http://localhost:${GATEWAY_PORT}/admin/actuator/health"
    # Refresh Docker DNS after a backend container has been recreated with a new IP.
    compose --profile cloud exec -T frontend-cloud nginx -s reload
    wait_for_url frontend "http://localhost:${BIXI_HTTP_PORT}/healthz"
    show_access
}

start_single() {
    doctor single
    load_env
    select_registry
    prepare_images single
    compose --profile cloud --profile workflow --profile workflow-cluster \
        stop frontend-cloud auth upms gateway workflow workflow-a workflow-b nacos nacos-config >/dev/null 2>&1 || true
    for service in single frontend-single; do
        compose --profile single build "${service}"
    done
    compose --profile single up --detach --no-build mysql redis rabbitmq single frontend-single
    wait_for_url single "http://localhost:${SINGLE_PORT}/admin/actuator/health"
    compose --profile single exec -T frontend-single nginx -s reload
    wait_for_url frontend "http://localhost:${BIXI_HTTP_PORT}/healthz"
    show_access
}

diagnose() {
    load_env
    require_docker
    compose --profile cloud --profile single --profile workflow ps
    for check in \
        "gateway|http://localhost:${GATEWAY_PORT}/actuator/health" \
        "auth|http://localhost:${GATEWAY_PORT}/auth/actuator/health" \
        "upms|http://localhost:${GATEWAY_PORT}/admin/actuator/health" \
        "single|http://localhost:${SINGLE_PORT}/admin/actuator/health" \
        "frontend|http://localhost:${BIXI_HTTP_PORT}/healthz"; do
        name="${check%%|*}"
        url="${check#*|}"
        if curl -fsS "${url}" >/dev/null 2>&1; then
            info "${name}: reachable"
        else
            info "${name}: not reachable"
        fi
    done
}

usage() {
    cat <<'USAGE'
Usage: scripts/bixi.sh COMMAND

Commands:
  init-env       Create .env with random local credentials
  doctor         Validate the cloud runtime prerequisites
  doctor-dev     Validate Docker, Java, Maven, Node.js, and npm
  start-cloud    Build, start, and health-check the cloud deployment
  start-single   Build, start, and health-check the single deployment
  status         Show container status
  diagnose       Probe all cloud and single health endpoints
  logs           Follow container logs
  stop           Stop containers and preserve data volumes
  reset          Stop containers and remove local data volumes
  credentials    Print the generated local URL and login
USAGE
}

command="${1:-}"
case "${command}" in
    init-env) init_env ;;
    doctor) doctor cloud ;;
    doctor-dev) doctor dev ;;
    start-cloud) start_cloud ;;
    start-single) start_single ;;
    status) load_env; require_docker; compose --profile cloud --profile single --profile workflow ps ;;
    diagnose) diagnose ;;
    logs) load_env; require_docker; compose --profile cloud --profile single --profile workflow logs --follow --tail=200 ;;
    stop) load_env; require_docker; compose --profile cloud --profile single --profile workflow down ;;
    reset) load_env; require_docker; compose --profile cloud --profile single --profile workflow down --volumes --remove-orphans ;;
    credentials) show_access ;;
    *) usage; [ -z "${command}" ] || exit 1 ;;
esac
