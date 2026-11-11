#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ENV_FILE="${BIXI_ENV_FILE:-${ROOT}/.env}"
COMPOSE_FILE="${ROOT}/compose.yaml"
RELEASE_ENV_FILE="${BIXI_RELEASE_ENV_FILE:-${ROOT}/.env.release}"
RELEASE_START=false
RELEASE_IMAGE_KEYS='BIXI_RELEASE_MYSQL_IMAGE BIXI_RELEASE_REDIS_IMAGE BIXI_RELEASE_RABBITMQ_IMAGE BIXI_RELEASE_NACOS_IMAGE BIXI_RELEASE_MONITOR_IMAGE BIXI_RELEASE_GATEWAY_IMAGE BIXI_RELEASE_AUTH_IMAGE BIXI_RELEASE_UPMS_IMAGE BIXI_RELEASE_GENERATOR_IMAGE BIXI_RELEASE_QUARTZ_IMAGE BIXI_RELEASE_AI_IMAGE BIXI_RELEASE_WORKFLOW_IMAGE BIXI_RELEASE_FRONTEND_CLOUD_IMAGE BIXI_RELEASE_SINGLE_IMAGE BIXI_RELEASE_FRONTEND_SINGLE_IMAGE BIXI_RELEASE_NGINX_IMAGE'

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
    elif [ -r /dev/urandom ] && command_exists od && command_exists tr; then
        random_bytes="$(od -An -N"${1:-24}" -tx1 /dev/urandom)" || fail 'secure random source failed'
        printf '%s' "${random_bytes}" | tr -d '[:space:]'
    else
        fail 'secure random source requires OpenSSL or /dev/urandom with od and tr'
    fi
}

encode_nacos_token() {
    command_exists base64 || return 1
    token_b64="$(printf '%s' "$1" | base64)" || return 1
    token_b64="$(printf '%s' "$token_b64" | tr -d '\n')" || return 1
    [ -n "$token_b64" ] || return 1
    printf '%s' "$token_b64"
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

append_random_env_if_missing() {
    key="$1"
    if ! grep -q "^${key}=" "${ENV_FILE}"; then
        generated_secret="$(random_hex 24)" || fail 'secure random source failed'
        append_env_if_missing "${key}" "${generated_secret}"
    fi
}

upgrade_env() {
    append_random_env_if_missing OAUTH_BROWSER_CLIENT_SECRET
    append_env_if_missing NACOS_USERNAME nacos
    append_random_env_if_missing NACOS_PASSWORD
    append_random_env_if_missing NACOS_CLIENT_PASSWORD
    append_env_if_missing NACOS_AUTH_IDENTITY_KEY bixi-service
    append_random_env_if_missing NACOS_AUTH_IDENTITY_VALUE
    if ! grep -q '^NACOS_AUTH_TOKEN=' "${ENV_FILE}"; then
        nacos_token_source="$(random_hex 32)" || fail 'secure random source failed'
        nacos_token_encoded="$(encode_nacos_token "$nacos_token_source")" \
            || fail 'NACOS_AUTH_TOKEN generation requires working base64'
        append_env_if_missing NACOS_AUTH_TOKEN "$nacos_token_encoded"
    fi
    append_env_if_missing DRUID_USERNAME bixi
    append_random_env_if_missing DRUID_PASSWORD
    append_env_if_missing MINIO_ENDPOINT http://127.0.0.1:9800
    append_random_env_if_missing MINIO_ACCESS_KEY
    append_random_env_if_missing MINIO_SECRET_KEY
    append_env_if_missing RABBITMQ_APP_USERNAME bixi-app
    append_random_env_if_missing RABBITMQ_APP_PASSWORD
    append_env_if_missing RABBITMQ_UPMS_USERNAME bixi-upms
    append_random_env_if_missing RABBITMQ_UPMS_PASSWORD
    append_env_if_missing RABBITMQ_WORKFLOW_USERNAME bixi-workflow
    append_random_env_if_missing RABBITMQ_WORKFLOW_PASSWORD
    append_env_if_missing RABBITMQ_WORKFLOW_VHOST /bixi-workflow
    append_env_if_missing BIXI_SBA_CLIENT_ENABLED true
    append_env_if_missing BIXI_SBA_CLIENT_URL http://monitor:5001
    append_env_if_missing BIXI_SBA_CLIENT_USERNAME monitor
    append_random_env_if_missing BIXI_SBA_CLIENT_PASSWORD
    append_env_if_missing BIXI_MANAGEMENT_ENDPOINTS health,info,metrics,loggers,logfile,dynamictp
    if grep -Eq '^BIXI_MANAGEMENT_ENDPOINTS=health,info,metrics(,loggers,logfile)?$' "${ENV_FILE}"; then
        replace_env BIXI_MANAGEMENT_ENDPOINTS health,info,metrics,loggers,logfile,dynamictp
    fi
    append_env_if_missing BIXI_RELIABLE_ENABLED false
    append_env_if_missing BIXI_RELIABLE_RABBIT_ENABLED false
    append_env_if_missing BIXI_HTTPS_ENABLED false
    append_env_if_missing BIXI_HTTPS_PORT 443
    append_env_if_missing BIXI_TLS_DIR ''
    append_env_if_missing AI_ENABLED false
    append_env_if_missing DASHSCOPE_API_KEY ''
    append_env_if_missing AI_CHAT_ENABLED true
    append_env_if_missing AI_EMBEDDING_ENABLED true
    append_env_if_missing AI_PORT 5000
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
    umask 077
    if [ -f "${ENV_FILE}" ]; then
        chmod 600 "${ENV_FILE}"
        upgrade_env
        info '.env already exists; added only missing runtime keys'
        return
    fi

    env_file_target="${ENV_FILE}"
    env_file_tmp="$(mktemp "${ENV_FILE}.tmp.XXXXXX")"
    trap 'rm -f "${env_file_tmp}" "${env_file_tmp}.tmp"' EXIT HUP INT TERM
    cp "${ROOT}/.env.example" "${env_file_tmp}"
    ENV_FILE="${env_file_tmp}"
    for key in MYSQL_PASSWORD MYSQL_ROOT_PASSWORD REDIS_PASSWORD RABBITMQ_PASSWORD \
        RABBITMQ_APP_PASSWORD RABBITMQ_UPMS_PASSWORD RABBITMQ_WORKFLOW_PASSWORD \
        BIXI_ENCODE_KEY JASYPT_ENCRYPTOR_PASSWORD OAUTH_PASSWORD_CLIENT_SECRET \
        OAUTH_MOBILE_CLIENT_SECRET OAUTH_BROWSER_CLIENT_SECRET OAUTH_INTERNAL_SEED \
        NACOS_PASSWORD NACOS_CLIENT_PASSWORD NACOS_AUTH_IDENTITY_VALUE \
        DRUID_PASSWORD MINIO_ACCESS_KEY MINIO_SECRET_KEY ADMIN_PASSWORD \
        BIXI_SBA_CLIENT_PASSWORD; do
        generated_secret="$(random_hex 24)" || fail 'secure random source failed'
        replace_env "${key}" "${generated_secret}"
    done
    nacos_token_source="$(random_hex 32)" || fail 'secure random source failed'
    nacos_token_encoded="$(encode_nacos_token "$nacos_token_source")" \
        || fail 'NACOS_AUTH_TOKEN generation requires working base64'
    replace_env NACOS_AUTH_TOKEN "$nacos_token_encoded"
    mv "${env_file_tmp}" "${env_file_target}"
    ENV_FILE="${env_file_target}"
    trap - EXIT HUP INT TERM
    info 'created .env with random local credentials'
}

load_env() {
    init_env
    set -a
    # shellcheck disable=SC1090
    . "${ENV_FILE}"
    set +a
}

validate_runtime_contract() {
    mode="${1:-cloud}"
    if [ "${WORKFLOW_ENABLED:-false}" != 'true' ]; then
        return
    fi
    [ "${BIXI_RELIABLE_ENABLED:-false}" = 'true' ] \
        || fail 'WORKFLOW_ENABLED=true requires BIXI_RELIABLE_ENABLED=true'
    if [ "${mode}" = 'cloud' ]; then
        [ "${BIXI_RELIABLE_RABBIT_ENABLED:-false}" = 'true' ] \
            || fail 'cloud WORKFLOW_ENABLED=true requires BIXI_RELIABLE_RABBIT_ENABLED=true'
        [ -n "${RABBITMQ_APP_USERNAME:-}" ] && [ -n "${RABBITMQ_APP_PASSWORD:-}" ] \
            || fail 'cloud Workflow requires non-empty Rabbit application credentials'
        [ -n "${RABBITMQ_UPMS_USERNAME:-}" ] && [ -n "${RABBITMQ_UPMS_PASSWORD:-}" ] \
            || fail 'cloud Workflow requires non-empty UPMS Rabbit credentials'
        [ -n "${RABBITMQ_WORKFLOW_USERNAME:-}" ] && [ -n "${RABBITMQ_WORKFLOW_PASSWORD:-}" ] \
            || fail 'cloud Workflow requires non-empty Workflow Rabbit credentials'
        [ "${RABBITMQ_WORKFLOW_VHOST:-/}" != '/' ] \
            || fail 'cloud Workflow requires a dedicated Rabbit virtual host'
        [ "${RABBITMQ_USERNAME}" != "${RABBITMQ_APP_USERNAME}" ] \
            && [ "${RABBITMQ_USERNAME}" != "${RABBITMQ_UPMS_USERNAME}" ] \
            && [ "${RABBITMQ_USERNAME}" != "${RABBITMQ_WORKFLOW_USERNAME}" ] \
            && [ "${RABBITMQ_APP_USERNAME}" != "${RABBITMQ_UPMS_USERNAME}" ] \
            && [ "${RABBITMQ_APP_USERNAME}" != "${RABBITMQ_WORKFLOW_USERNAME}" ] \
            && [ "${RABBITMQ_UPMS_USERNAME}" != "${RABBITMQ_WORKFLOW_USERNAME}" ] \
            || fail 'cloud Workflow Rabbit bootstrap, application, UPMS, and Workflow users must differ'
    fi
}

validate_ai_contract() {
    if [ "${AI_ENABLED:-false}" = 'true' ]; then
        [ -n "${DASHSCOPE_API_KEY:-}" ] && [ "${DASHSCOPE_API_KEY}" != 'GENERATE_ON_FIRST_RUN' ] \
            || fail 'AI_ENABLED=true requires DASHSCOPE_API_KEY; keep AI_ENABLED=false until a provider key is supplied'
    fi
}

validate_https_contract() {
    case "${BIXI_HTTPS_ENABLED:-false}" in
        false) return ;;
        true) ;;
        *) fail 'BIXI_HTTPS_ENABLED must be true or false' ;;
    esac
    case "${BIXI_TLS_DIR:-}" in
        /*) ;;
        *) fail 'BIXI_TLS_DIR must be an absolute directory containing fullchain.pem and privkey.pem' ;;
    esac
    for certificate_file in fullchain.pem privkey.pem; do
        [ -s "${BIXI_TLS_DIR}/${certificate_file}" ] \
            || fail "BIXI_TLS_DIR is missing ${certificate_file}"
    done
    case "${BIXI_HTTPS_PORT:-443}" in
        ''|*[!0-9]*) fail 'BIXI_HTTPS_PORT must be a numeric TCP port' ;;
    esac
    [ "${BIXI_HTTPS_PORT:-443}" -ge 1 ] && [ "${BIXI_HTTPS_PORT:-443}" -le 65535 ] \
        || fail 'BIXI_HTTPS_PORT must be between 1 and 65535'
}

validate_browser_secret() {
    case "${OAUTH_BROWSER_CLIENT_SECRET:-}" in
        *[!0-9a-fA-F]*|'') fail 'OAUTH_BROWSER_CLIENT_SECRET must be 48 hexadecimal characters' ;;
    esac
    [ "${#OAUTH_BROWSER_CLIENT_SECRET}" -eq 48 ] \
        || fail 'OAUTH_BROWSER_CLIENT_SECRET must be 48 hexadecimal characters'
}

validate_config() {
    mode="${1:-cloud}"
    case "${mode}" in
        cloud|single) ;;
        *) fail "validation mode must be cloud or single; found ${mode}" ;;
    esac
    load_env
    validate_browser_secret
    validate_runtime_contract "${mode}"
    validate_ai_contract
    validate_https_contract
    info "runtime configuration is valid (${mode})"
}

load_release_env() {
    [ -r "${ENV_FILE}" ] || fail "missing runtime environment: ${ENV_FILE}; run init-env and set production credentials first"
    [ -r "${RELEASE_ENV_FILE}" ] || fail "missing release manifest: ${RELEASE_ENV_FILE}"
    set -a
    # shellcheck disable=SC1090
    . "${ENV_FILE}"
    unset commit version mode
    for key in ${RELEASE_IMAGE_KEYS}; do unset "${key}"; done
    # shellcheck disable=SC1090
    . "${RELEASE_ENV_FILE}"
    set +a
}

validate_release_manifest() {
    [ "${version:-}" = '1.0.0' ] && [ "${mode:-}" = 'compose' ] \
        || fail 'release manifest must declare version=1.0.0 and mode=compose'
    [ -z "$(git -C "${ROOT}" status --porcelain --untracked-files=normal)" ] \
        || fail 'source tree is not frozen; commit all release changes first'
    head_commit="$(git -C "${ROOT}" rev-parse HEAD)" \
        || fail 'unable to read the frozen source commit'
    [ "${commit:-}" = "${head_commit}" ] \
        || fail 'release manifest frozen commit does not match HEAD'
    for key in ${RELEASE_IMAGE_KEYS}; do
        image="$(printenv "${key}" || true)"
        case "${image}" in
            ?*@sha256:*) ;;
            *) fail "${key} must be a digest-pinned image reference" ;;
        esac
        digest="${image##*@sha256:}"
        case "${digest}" in
            *[!0-9a-fA-F]*|'') fail "${key} must be a digest-pinned image reference" ;;
        esac
        [ "${#digest}" -eq 64 ] \
            || fail "${key} must be a digest-pinned image reference"
    done
}

require_release_compose() {
    compose_version="$(docker compose version --short 2>/dev/null)" \
        || fail 'Docker Compose 2.24+ is required for release !reset overrides'
    compose_major="${compose_version%%.*}"
    compose_minor="${compose_version#*.}"
    compose_minor="${compose_minor%%.*}"
    case "${compose_major}:${compose_minor}" in
        *[!0-9:]*|:*|*:) fail "Docker Compose 2.24+ is required; found ${compose_version}" ;;
    esac
    if [ "${compose_major}" -lt 2 ] \
        || { [ "${compose_major}" -eq 2 ] && [ "${compose_minor}" -lt 24 ]; }; then
        fail "Docker Compose 2.24+ is required; found ${compose_version}"
    fi
}

check_release_mysql_volume() {
    mysql_volume="${COMPOSE_PROJECT_NAME:-bixi}_mysql-data"
    volume_names="$(docker volume ls --format '{{.Name}}')" \
        || fail 'unable to list Docker volumes'
    if ! printf '%s\n' "${volume_names}" | grep -Fxq "${mysql_volume}"; then
        return
    fi
    volume_commit="$(docker volume inspect --format '{{ index .Labels "com.bixi.release.commit" }}' "${mysql_volume}")" \
        || fail "unable to inspect existing MySQL volume ${mysql_volume}"
    [ "${volume_commit}" = "${commit}" ] \
        || fail "existing MySQL volume is not from frozen release: ${mysql_volume}"
}

doctor_release() {
    release_target_mode="$1"
    load_release_env
    validate_release_manifest
    validate_browser_secret
    validate_runtime_contract "${release_target_mode}"
    validate_ai_contract
    if [ "${release_target_mode}" = 'cloud' ]; then
        [ "${NACOS_NAMESPACE:-}" = 'bixi-prod' ] \
            || fail 'cloud release requires NACOS_NAMESPACE=bixi-prod'
        for key in NACOS_USERNAME NACOS_PASSWORD NACOS_CLIENT_PASSWORD NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE; do
            secret="$(printenv "${key}" || true)"
            [ -n "${secret}" ] && [ "${secret}" != 'GENERATE_ON_FIRST_RUN' ] \
                || fail "cloud release requires configured ${key}"
        done
        [ "${NACOS_USERNAME}" = 'nacos' ] \
            || fail 'cloud release requires NACOS_USERNAME=nacos for first-install bootstrap'
        [ "${NACOS_CLIENT_PASSWORD}" != "${NACOS_PASSWORD}" ] \
            || fail 'NACOS_CLIENT_PASSWORD must differ from NACOS_PASSWORD'
        case "${NACOS_AUTH_TOKEN:-}" in
            *[!A-Za-z0-9+/=]*|''|GENERATE_ON_FIRST_RUN) fail 'cloud release requires configured NACOS_AUTH_TOKEN' ;;
        esac
        printf '%s' "${NACOS_AUTH_TOKEN}" | base64 -d >/dev/null 2>&1 \
            || fail 'NACOS_AUTH_TOKEN must be base64 encoded'
        [ "$(printf '%s' "${NACOS_AUTH_TOKEN}" | base64 -d | wc -c)" -ge 32 ] \
            || fail 'NACOS_AUTH_TOKEN must decode to at least 32 bytes'
    fi
    [ "${BIXI_HTTPS_ENABLED:-false}" = 'true' ] \
        || fail 'release requires BIXI_HTTPS_ENABLED=true'
    validate_https_contract
    for key in MYSQL_PASSWORD MYSQL_ROOT_PASSWORD REDIS_PASSWORD \
        RABBITMQ_PASSWORD RABBITMQ_APP_PASSWORD RABBITMQ_UPMS_PASSWORD \
        RABBITMQ_WORKFLOW_PASSWORD BIXI_ENCODE_KEY JASYPT_ENCRYPTOR_PASSWORD \
        OAUTH_PASSWORD_CLIENT_SECRET OAUTH_MOBILE_CLIENT_SECRET OAUTH_INTERNAL_SEED \
        DRUID_PASSWORD MINIO_ACCESS_KEY MINIO_SECRET_KEY BIXI_SBA_CLIENT_PASSWORD; do
        secret="$(printenv "${key}" || true)"
        [ -n "${secret}" ] && [ "${secret}" != 'GENERATE_ON_FIRST_RUN' ] \
            || fail "release requires configured ${key}"
    done
    admin_hash="$(printf '%s' "${ADMIN_PASSWORD_BCRYPT_B64:-}" | base64 -d 2>/dev/null)" \
        || fail 'release requires precomputed ADMIN_PASSWORD_BCRYPT_B64'
    printf '%s\n' "${admin_hash}" \
        | grep -Eq '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$' \
        || fail 'release requires precomputed ADMIN_PASSWORD_BCRYPT_B64'
    require_docker
    require_release_compose
    compose --profile cloud --profile ai --profile single --profile workflow config --quiet
    check_release_mysql_volume
    check_ports "${release_target_mode}"
    info "release prerequisites passed (${release_target_mode}, ${commit})"
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
    ensure_image "${prefix}library/maven:3.9.9-eclipse-temurin-17"
    ensure_image "${prefix}library/eclipse-temurin:17.0.13_11-jre-jammy"
    ensure_image "${prefix}library/node:22.23.1-alpine"
    ensure_image "${prefix}library/nginx:1.27.3-alpine3.20"
    if [ "${mode}" = 'cloud' ]; then
        ensure_image "${prefix}library/rabbitmq:4.0.5-management-alpine"
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
        hash="$(printf '%s\n' "${ADMIN_PASSWORD}" | htpasswd -niBC 10 '' | tr -d ':\n')"
    else
        hash="$(printf '%s\n' "${ADMIN_PASSWORD}" | docker run --rm -i httpd:2.4.62-alpine htpasswd -niBC 10 '' | tr -d ':\n')"
    fi
    [ -n "$hash" ] || fail 'administrator bcrypt generation failed'
    encoded="$(printf '%s' "${hash}" | base64 | tr -d '\n')"
    replace_env ADMIN_PASSWORD_BCRYPT_B64 "${encoded}"
}

compose() {
    if [ "${RELEASE_START}" = 'true' ]; then
        set -- -f "${ROOT}/compose.release.yaml" "$@"
    fi
    if [ "${BIXI_HTTPS_ENABLED:-false}" = 'true' ]; then
        set -- -f "${ROOT}/compose.https.yaml" "$@"
    fi
    if [ "${WORKFLOW_CLUSTER_ENABLED:-false}" = 'true' ]; then
        set -- -f "${ROOT}/compose.workflow-cluster.yaml" "$@"
    fi
    docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

stop_https_services() {
    BIXI_TLS_DIR=/tmp BIXI_HTTPS_PORT=443 docker compose --env-file "${ENV_FILE}" \
        -f "${COMPOSE_FILE}" -f "${ROOT}/compose.https.yaml" \
        --profile cloud --profile single stop https-cloud https-single >/dev/null
}

# The active configuration can no longer describe a Workflow service from the
# previous cluster mode after WORKFLOW_CLUSTER_ENABLED is toggled. Always load
# both Compose files when stopping these services so an old consumer cannot
# continue polling the shared inbox during the transition.
stop_workflow_services() {
    docker compose --env-file "${ENV_FILE}" \
        -f "${COMPOSE_FILE}" \
        -f "${ROOT}/compose.workflow-cluster.yaml" \
        --profile cloud --profile workflow --profile workflow-cluster \
        stop workflow workflow-a workflow-b >/dev/null 2>&1
}

compose_owns_port() {
    port="$1"
    compose --profile cloud --profile ai --profile single --profile workflow ps --format json 2>/dev/null \
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
    if [ "${BIXI_HTTPS_ENABLED:-false}" = 'true' ]; then
        check_port BIXI_HTTPS_PORT "${BIXI_HTTPS_PORT:-443}"
    fi
    check_port MYSQL_PORT "${MYSQL_PORT}"
    check_port REDIS_PORT "${REDIS_PORT}"
    if [ "${mode}" = 'single' ]; then
        check_port SINGLE_PORT "${SINGLE_PORT}"
    else
        check_port RABBITMQ_PORT "${RABBITMQ_PORT}"
        check_port RABBITMQ_MANAGEMENT_PORT "${RABBITMQ_MANAGEMENT_PORT}"
        check_port GATEWAY_PORT "${GATEWAY_PORT}"
        check_port NACOS_PORT "${NACOS_PORT}"
        check_port MONITOR_PORT "${MONITOR_PORT:-5001}"
        if [ "${AI_ENABLED:-false}" = 'true' ]; then
            check_port AI_PORT "${AI_PORT:-5000}"
        fi
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
    validate_browser_secret
    validate_runtime_contract "${mode}"
    validate_ai_contract
    validate_https_contract
    require_docker
    prepare_admin_hash
    compose --profile cloud --profile ai --profile single --profile workflow config --quiet
    check_ports "${mode}"

    for file in \
        bixi-project-documents/sql/01_schema.sql \
        bixi-project-documents/sql/02_data.sql \
        bixi-project-documents/sql/03_constraints.sql \
        bixi-project-documents/sql/04_indexes.sql; do
        [ -s "${ROOT}/${file}" ] || fail "missing database asset: ${file}"
    done

    if [ "${mode}" = 'dev' ]; then
        command_exists java || fail 'Java 17+ is not installed'
        command_exists mvn || fail 'Maven 3.8+ is not installed'
        command_exists node || fail 'Node.js 22.18+ is not installed'
        command_exists npm || fail 'npm 8+ is not installed'
        check_version Java 17 "$(java -version 2>&1 | head -n 1)"
        check_version Maven 3 "$(mvn -version 2>&1 | head -n 1)"
        node -e 'const [major, minor] = process.versions.node.split(".").map(Number); process.exit(major > 22 || (major === 22 && minor >= 18) ? 0 : 1)' \
            || fail "Node.js 22.18+ is required; found $(node --version)"
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
    if [ "${RELEASE_START}" = 'true' ]; then
        info "HTTPS listening on port ${BIXI_HTTPS_PORT:-443}; use the certificate hostname"
    else
        load_env
        info "URL: http://localhost:${BIXI_HTTP_PORT}"
    fi
    info "username: ${ADMIN_USERNAME}"
}

show_credentials() {
    show_access
    info "password: ${ADMIN_PASSWORD}"
}

start_cloud() {
    if [ "${RELEASE_START}" = 'true' ]; then
        doctor_release cloud
    else
        doctor cloud
        load_env
    fi
    stop_workflow_services
    stop_https_services
    validate_ai_contract
    if [ "${RELEASE_START}" != 'true' ]; then
        select_registry
        prepare_images cloud
    fi
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
    fi
    ai_services=""
    ai_profiles=""
    if [ "${AI_ENABLED:-false}" = 'true' ]; then
        ai_services="ai"
        ai_profiles="--profile ai"
        BIXI_GATEWAY_PROFILES_ACTIVE=ai
        export BIXI_GATEWAY_PROFILES_ACTIVE
    else
        BIXI_GATEWAY_PROFILES_ACTIVE=
        export BIXI_GATEWAY_PROFILES_ACTIVE
        compose --profile cloud --profile ai stop ai >/dev/null 2>&1 || true
    fi
    if [ "${RELEASE_START}" != 'true' ]; then
        for service in monitor gateway upms auth generator quartz frontend-cloud ${ai_services} ${workflow_services}; do
            compose --profile cloud ${ai_profiles} build "${service}"
        done
    fi
    compose --profile cloud ${ai_profiles} up --detach --no-build \
        mysql redis rabbitmq nacos nacos-config monitor gateway upms auth generator quartz frontend-cloud ${ai_services}
    if [ -n "${workflow_services}" ]; then
        compose --profile cloud ${workflow_profiles} up --detach --no-build --wait ${workflow_services}
    fi
    wait_for_url gateway "http://localhost:${GATEWAY_PORT}/actuator/health"
    wait_for_url monitor "http://localhost:${MONITOR_PORT:-5001}/actuator/health"
    wait_for_url auth "http://localhost:${GATEWAY_PORT}/auth/actuator/health"
    wait_for_url upms "http://localhost:${GATEWAY_PORT}/admin/actuator/health"
    wait_for_url generator "http://localhost:${GATEWAY_PORT}/gen/actuator/health"
    wait_for_url quartz "http://localhost:${GATEWAY_PORT}/job/actuator/health"
    if [ -n "${ai_services}" ]; then
        wait_for_url ai "http://localhost:${AI_PORT:-5000}/actuator/health"
    fi
    # Refresh Docker DNS after a backend container has been recreated with a new IP.
    compose --profile cloud exec -T frontend-cloud nginx -s reload
    wait_for_url frontend "http://localhost:${BIXI_HTTP_PORT}/healthz"
    if [ "${BIXI_HTTPS_ENABLED:-false}" = 'true' ]; then
        compose --profile cloud up --detach --no-build https-cloud
        # The local readiness probe uses localhost, not the certificate's public hostname.
        curl -kfsS --retry 12 --retry-connrefused --retry-delay 1 \
            "https://localhost:${BIXI_HTTPS_PORT:-443}/healthz" >/dev/null \
            || fail 'HTTPS cloud edge did not become ready'
        info 'HTTPS cloud edge is ready'
    fi
    show_access
}

start_single() {
    if [ "${RELEASE_START}" = 'true' ]; then
        doctor_release single
    else
        doctor single
        load_env
    fi
    stop_workflow_services
    stop_https_services
    if [ "${RELEASE_START}" != 'true' ]; then
        select_registry
        prepare_images single
    fi
    compose --profile cloud --profile ai --profile workflow --profile workflow-cluster \
        stop frontend-cloud monitor auth upms generator quartz ai gateway nacos nacos-config >/dev/null 2>&1 || true
    if [ "${RELEASE_START}" != 'true' ]; then
        for service in single frontend-single; do
            compose --profile single build "${service}"
        done
    fi
    compose --profile single up --detach --no-build mysql redis single frontend-single
    wait_for_url single "http://localhost:${SINGLE_PORT}/admin/actuator/health"
    compose --profile single exec -T frontend-single nginx -s reload
    wait_for_url frontend "http://localhost:${BIXI_HTTP_PORT}/healthz"
    if [ "${BIXI_HTTPS_ENABLED:-false}" = 'true' ]; then
        compose --profile single up --detach --no-build https-single
        # The local readiness probe uses localhost, not the certificate's public hostname.
        curl -kfsS --retry 12 --retry-connrefused --retry-delay 1 \
            "https://localhost:${BIXI_HTTPS_PORT:-443}/healthz" >/dev/null \
            || fail 'HTTPS single edge did not become ready'
        info 'HTTPS single edge is ready'
    fi
    show_access
}

diagnose() {
    load_env
    require_docker
    compose --profile cloud --profile ai --profile single --profile workflow ps
    if [ "${AI_ENABLED:-false}" = 'true' ]; then
        ai_diagnose="ai|http://localhost:${AI_PORT:-5000}/actuator/health"
    else
        ai_diagnose=""
    fi
    for check in \
        "gateway|http://localhost:${GATEWAY_PORT}/actuator/health" \
        "monitor|http://localhost:${MONITOR_PORT:-5001}/actuator/health" \
        "auth|http://localhost:${GATEWAY_PORT}/auth/actuator/health" \
        "upms|http://localhost:${GATEWAY_PORT}/admin/actuator/health" \
        "generator|http://localhost:${GATEWAY_PORT}/gen/actuator/health" \
        "quartz|http://localhost:${GATEWAY_PORT}/job/actuator/health" \
        ${ai_diagnose:+"${ai_diagnose}"} \
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
  validate-config Validate runtime feature dependencies (cloud by default)
  doctor         Validate the cloud runtime prerequisites
  doctor-dev     Validate Docker, Java, Maven, Node.js, and npm
  start-cloud    Build, start, and health-check the cloud deployment
  start-single   Build, start, and health-check the single deployment
  start-cloud-release  Start cloud from frozen digest-pinned images
  start-single-release Start single from frozen digest-pinned images
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
    validate-config) validate_config "${2:-cloud}" ;;
    doctor) doctor cloud ;;
    doctor-dev) doctor dev ;;
    start-cloud) start_cloud ;;
    start-single) start_single ;;
    start-cloud-release) RELEASE_START=true; start_cloud ;;
    start-single-release) RELEASE_START=true; start_single ;;
    status) load_env; require_docker; compose --profile cloud --profile ai --profile single --profile workflow ps ;;
    diagnose) diagnose ;;
    logs) load_env; require_docker; compose --profile cloud --profile ai --profile single --profile workflow logs --follow --tail=200 ;;
    stop) load_env; require_docker; stop_https_services; stop_workflow_services; BIXI_HTTPS_ENABLED=false; compose --profile cloud --profile ai --profile single --profile workflow down ;;
    reset) load_env; require_docker; stop_https_services; stop_workflow_services; BIXI_HTTPS_ENABLED=false; compose --profile cloud --profile ai --profile single --profile workflow down --volumes --remove-orphans ;;
    credentials) show_credentials ;;
    *) usage; [ -z "${command}" ] || exit 1 ;;
esac
