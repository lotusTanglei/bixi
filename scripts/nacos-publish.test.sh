#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "${TEST_DIR}"' EXIT HUP INT TERM
mkdir -p "${TEST_DIR}/bin" "${TEST_DIR}/config"
printf 'test: true\n' >"${TEST_DIR}/config/application-prod.yml"

cat >"${TEST_DIR}/bin/curl" <<'CURL'
#!/bin/sh
set -eu
for arg in "$@"; do
    case "$arg" in
        *"$NACOS_PASSWORD"*|*"$NACOS_CLIENT_PASSWORD"*)
            echo 'Nacos password appeared in curl arguments' >&2
            exit 1 ;;
        password@-) ;;
        password@*)
            [ "$(cat "${arg#password@}")" = "$NACOS_CLIENT_PASSWORD" ] \
                || { echo 'client password file was not used' >&2; exit 1; } ;;
        newPassword@*)
            [ "$(cat "${arg#newPassword@}")" = "$NACOS_CLIENT_PASSWORD" ] \
                || { echo 'updated client password file was not used' >&2; exit 1; } ;;
    esac
done
printf '%s\n' "$*" >>"$BIXI_TEST_CURL_LOG"
case "$*" in
    *'/v1/auth/users/login'*)
        [ "$(cat)" = "$NACOS_PASSWORD" ] || exit 1
        printf '{"accessToken":"fixture-token"}' ;;
    *'/v1/auth/users'*)
        [ "$(cat)" = 'fixture-token' ] || exit 1
        case "$*" in
            *'-X POST '*) [ "${BIXI_TEST_USER_EXISTS:-false}" = false ] || exit 22 ;;
            *'-X PUT '*)
                [ "${BIXI_TEST_USER_EXISTS:-false}" = true ] || exit 1
                case "$*" in
                    *'newPassword@'*) ;;
                    *) echo 'Nacos user update requires newPassword' >&2; exit 1 ;;
                esac ;;
        esac
        printf 'true' ;;
    *'/v1/auth/roles'*)
        case "$*" in
            *'-X GET '*)
                [ "$(cat)" = 'header = "Authorization: Bearer fixture-token"' ] || exit 1
                case "$*" in
                    *' -K - -G '*'search=accurate'*'pageNo=1'*'pageSize=1'*'role=bixi-prod-client'*'username=bixi-prod-client'*) ;;
                    *) echo 'Nacos role lookup must use exact search' >&2; exit 1 ;;
                esac
                if [ "${BIXI_TEST_ACL_EXISTS:-false}" = true ]; then
                    printf '{"totalCount":1,"pageItems":[{"role":"bixi-prod-client","username":"bixi-prod-client"}]}'
                else
                    printf '{"totalCount":0,"pageItems":[]}'
                fi ;;
            *'-X POST '*)
                [ "$(cat)" = 'fixture-token' ] || exit 1
                [ "${BIXI_TEST_ACL_EXISTS:-false}" = false ] || exit 22
                printf 'true' ;;
            *) exit 1 ;;
        esac ;;
    *'/v1/auth/permissions'*)
        case "$*" in
            *'-X GET '*)
                [ "$(cat)" = 'header = "Authorization: Bearer fixture-token"' ] || exit 1
                case "$*" in
                    *' -K - -G '*'search=accurate'*'pageNo=1'*'pageSize=100'*'role=bixi-prod-client'*) ;;
                    *) echo 'Nacos permission lookup must use exact search' >&2; exit 1 ;;
                esac
                if [ "${BIXI_TEST_ACL_EXISTS:-false}" = true ]; then
                    printf '{"totalCount":2,"pageItems":[{"role":"bixi-prod-client","resource":"bixi-prod:*:config/*","action":"r"},{"role":"bixi-prod-client","resource":"bixi-prod:*:naming/*","action":"rw"}]}'
                else
                    printf '{"totalCount":0,"pageItems":[]}'
                fi ;;
            *'-X POST '*)
                [ "$(cat)" = 'fixture-token' ] || exit 1
                [ "${BIXI_TEST_ACL_EXISTS:-false}" = false ] || exit 22
                printf 'true' ;;
            *) exit 1 ;;
        esac ;;
    *'accessToken@-'*)
        [ "$(cat)" = 'fixture-token' ] || exit 1
        printf 'true' ;;
    *) exit 1 ;;
esac
CURL
chmod +x "${TEST_DIR}/bin/curl"

export PATH="${TEST_DIR}/bin:${PATH}"
export BIXI_TEST_CURL_LOG="${TEST_DIR}/curl.log"
export NACOS_AUTH_ENABLE=true NACOS_HOST=fixture NACOS_PORT=8848
export NACOS_NAMESPACE=bixi-prod NACOS_USERNAME=nacos
export NACOS_PASSWORD=fixture-admin-password NACOS_CLIENT_PASSWORD=fixture-client-password
export NACOS_CONFIG_DIR="${TEST_DIR}/config"

if output="$(NACOS_CLIENT_PASSWORD= sh "${ROOT}/deploy/nacos/publish.sh" 2>&1)"; then
    echo 'Publisher accepted a missing client password' >&2
    exit 1
fi
case "$output" in *NACOS_CLIENT_PASSWORD*) ;; *) echo "$output" >&2; exit 1 ;; esac
if output="$(NACOS_CLIENT_PASSWORD="$NACOS_PASSWORD" sh "${ROOT}/deploy/nacos/publish.sh" 2>&1)"; then
    echo 'Publisher accepted the administrator password for applications' >&2
    exit 1
fi
case "$output" in *'must differ'*) ;; *) echo "$output" >&2; exit 1 ;; esac

: >"${BIXI_TEST_CURL_LOG}"
sh "${ROOT}/deploy/nacos/publish.sh" >/dev/null
for required in \
    '/v1/auth/users' \
    '-X POST http://fixture:8848/nacos/v1/auth/roles' \
    'role=bixi-prod-client' \
    'username=bixi-prod-client' \
    'resource=bixi-prod:*:config/* --data-urlencode action=r' \
    'resource=bixi-prod:*:naming/* --data-urlencode action=rw' \
    '-X POST http://fixture:8848/nacos/v1/auth/permissions' \
    '/v1/cs/configs'; do
    grep -F -- "$required" "${BIXI_TEST_CURL_LOG}" >/dev/null \
        || { echo "Publisher missed ${required}" >&2; exit 1; }
done

: >"${BIXI_TEST_CURL_LOG}"
BIXI_TEST_USER_EXISTS=true BIXI_TEST_ACL_EXISTS=true \
    sh "${ROOT}/deploy/nacos/publish.sh" >/dev/null
grep -F -- '-X PUT http://fixture:8848/nacos/v1/auth/users' \
    "${BIXI_TEST_CURL_LOG}" >/dev/null \
    || { echo 'Publisher did not update the existing Nacos client' >&2; exit 1; }
for resource in roles permissions; do
    grep -F -- "-X GET http://fixture:8848/nacos/v1/auth/${resource}" \
        "${BIXI_TEST_CURL_LOG}" >/dev/null \
        || { echo "Publisher did not check existing Nacos ${resource}" >&2; exit 1; }
    if grep -F -- "-X POST http://fixture:8848/nacos/v1/auth/${resource}" \
        "${BIXI_TEST_CURL_LOG}" >/dev/null; then
        echo "Publisher repeated existing Nacos ${resource}" >&2
        exit 1
    fi
done

printf 'Nacos publisher ACL tests passed.\n'
