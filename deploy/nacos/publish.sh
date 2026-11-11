#!/bin/sh
set -eu

nacos_url="http://${NACOS_HOST:-nacos}:${NACOS_PORT:-8848}/nacos"
namespace="${NACOS_NAMESPACE:-bixi}"
group="${NACOS_GROUP:-DEFAULT_GROUP}"
config_dir="${NACOS_CONFIG_DIR:-/config}"
# Compose mounts deploy/nacos at /config/*.yml; direct deployments can override
# the directory with NACOS_CONFIG_DIR.
auth_token=''

nacos_login() {
    printf '%s' "$NACOS_PASSWORD" | curl -fsS -X POST "${nacos_url}/v1/auth/users/login" \
        --data-urlencode "username=${NACOS_USERNAME}" --data-urlencode 'password@-'
}

nacos_request() {
    method="$1"
    endpoint="$2"
    shift 2
    if [ "$method" = GET ] && [ -n "$auth_token" ]; then
        # Feed curl the header through stdin so the token stays out of URLs and argv.
        printf 'header = "Authorization: Bearer %s"\n' "$auth_token" | \
            curl -fsS -X GET "${nacos_url}${endpoint}" -K - "$@"
    elif [ -n "$auth_token" ]; then
        printf '%s' "$auth_token" | curl -fsS -X "$method" "${nacos_url}${endpoint}" \
            --data-urlencode 'accessToken@-' "$@"
    else
        curl -fsS -X "$method" "${nacos_url}${endpoint}" "$@"
    fi
}

nacos_post() {
    nacos_request POST "$@"
}

if [ "${NACOS_AUTH_ENABLE:-false}" = 'true' ]; then
    [ "${NACOS_USERNAME:-}" = 'nacos' ] && [ -n "${NACOS_PASSWORD:-}" ] \
        || { echo 'Nacos release requires the administrator credentials' >&2; exit 1; }
    [ "$namespace" = 'bixi-prod' ] \
        || { echo 'Authenticated Nacos publisher requires namespace bixi-prod' >&2; exit 1; }
    [ -n "${NACOS_CLIENT_PASSWORD:-}" ] && [ "$NACOS_CLIENT_PASSWORD" != 'GENERATE_ON_FIRST_RUN' ] \
        || { echo 'NACOS_CLIENT_PASSWORD is required' >&2; exit 1; }
    [ "$NACOS_CLIENT_PASSWORD" != "$NACOS_PASSWORD" ] \
        || { echo 'NACOS_CLIENT_PASSWORD must differ from NACOS_PASSWORD' >&2; exit 1; }
    login_response="$(nacos_login 2>/dev/null || true)"
    auth_token="$(printf '%s' "$login_response" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
    if [ -z "$auth_token" ]; then
        printf '%s' "$NACOS_PASSWORD" | curl -fsS -X POST "${nacos_url}/v1/auth/users/admin" \
            --data-urlencode 'password@-' >/dev/null \
            || { echo 'Nacos administrator initialization failed' >&2; exit 1; }
        login_response="$(nacos_login)" || { echo 'Nacos administrator login failed' >&2; exit 1; }
        auth_token="$(printf '%s' "$login_response" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
    fi
    case "$auth_token" in
        ''|*[!A-Za-z0-9._-]*) echo 'Nacos administrator login returned no usable token' >&2; exit 1 ;;
    esac
fi

nacos_post /v1/console/namespaces \
    --data-urlencode "customNamespaceId=${namespace}" \
    --data-urlencode "namespaceName=${namespace}" >/dev/null 2>&1 || true

if [ "${NACOS_AUTH_ENABLE:-false}" = 'true' ]; then
    client_password_file="$(mktemp)"
    trap 'rm -f "$client_password_file"' EXIT HUP INT TERM
    printf '%s' "$NACOS_CLIENT_PASSWORD" >"$client_password_file"
    if ! nacos_post /v1/auth/users \
        --data-urlencode 'username=bixi-prod-client' \
        --data-urlencode "password@${client_password_file}" >/dev/null 2>&1; then
        nacos_request PUT /v1/auth/users \
            --data-urlencode 'username=bixi-prod-client' \
            --data-urlencode "newPassword@${client_password_file}" >/dev/null \
            || { echo 'Failed to update Nacos client user' >&2; exit 1; }
    fi
    # Nacos inserts ACL rows and rejects duplicates on a repeated publish.
    roles="$(nacos_request GET /v1/auth/roles -G \
        --data-urlencode 'search=accurate' \
        --data-urlencode 'pageNo=1' --data-urlencode 'pageSize=1' \
        --data-urlencode 'role=bixi-prod-client' \
        --data-urlencode 'username=bixi-prod-client')" \
        || { echo 'Failed to inspect Nacos client role' >&2; exit 1; }
    if ! printf '%s\n' "$roles" | tr '{' '\n' | grep -F '"role":"bixi-prod-client"' \
        | grep -F '"username":"bixi-prod-client"' >/dev/null; then
        nacos_post /v1/auth/roles \
            --data-urlencode 'role=bixi-prod-client' \
            --data-urlencode 'username=bixi-prod-client' >/dev/null \
            || { echo 'Failed to assign Nacos client role' >&2; exit 1; }
    fi
    permissions="$(nacos_request GET /v1/auth/permissions -G \
        --data-urlencode 'search=accurate' \
        --data-urlencode 'pageNo=1' --data-urlencode 'pageSize=100' \
        --data-urlencode 'role=bixi-prod-client')" \
        || { echo 'Failed to inspect Nacos client permissions' >&2; exit 1; }
    if ! printf '%s\n' "$permissions" | tr '{' '\n' | grep -F '"resource":"bixi-prod:*:config/*"' \
        | grep -F '"action":"r"' >/dev/null; then
        nacos_post /v1/auth/permissions \
            --data-urlencode 'role=bixi-prod-client' \
            --data-urlencode 'resource=bixi-prod:*:config/*' \
            --data-urlencode 'action=r' >/dev/null \
            || { echo 'Failed to grant Nacos config read permission' >&2; exit 1; }
    fi
    if ! printf '%s\n' "$permissions" | tr '{' '\n' | grep -F '"resource":"bixi-prod:*:naming/*"' \
        | grep -F '"action":"rw"' >/dev/null; then
        nacos_post /v1/auth/permissions \
            --data-urlencode 'role=bixi-prod-client' \
            --data-urlencode 'resource=bixi-prod:*:naming/*' \
            --data-urlencode 'action=rw' >/dev/null \
            || { echo 'Failed to grant Nacos service discovery permission' >&2; exit 1; }
    fi
fi

for file in "${config_dir}"/*.yml; do
    [ -f "${file}" ] || {
        echo "No Nacos YAML files found in ${config_dir}" >&2
        exit 1
    }
    data_id="$(basename "${file}")"
    response="$(nacos_post /v1/cs/configs \
        --data-urlencode "dataId=${data_id}" \
        --data-urlencode "group=${group}" \
        --data-urlencode "tenant=${namespace}" \
        --data-urlencode "type=yaml" \
        --data-urlencode "content@${file}")"
    if [ "${response}" != "true" ]; then
        echo "Failed to publish Nacos config: ${data_id}" >&2
        exit 1
    fi
    echo "Published ${data_id}"
done
