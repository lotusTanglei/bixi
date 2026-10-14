#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "${TEST_DIR}"' EXIT HUP INT TERM

write_env() {
    workflow_enabled="$1"
    reliable_enabled="$2"
    rabbit_enabled="$3"
    env_file="${TEST_DIR}/runtime.env"
    sed "s/^WORKFLOW_ENABLED=.*/WORKFLOW_ENABLED=${workflow_enabled}/" \
        "${ROOT}/.env.example" >"${env_file}"
    printf '\nBIXI_RELIABLE_ENABLED=%s\nBIXI_RELIABLE_RABBIT_ENABLED=%s\n' \
        "${reliable_enabled}" "${rabbit_enabled}" >>"${env_file}"
    printf 'OAUTH_BROWSER_CLIENT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef\n' >>"${env_file}"
}

assert_valid() {
    mode="$1"
    if ! output="$(BIXI_ENV_FILE="${TEST_DIR}/runtime.env" \
            "${ROOT}/scripts/bixi.sh" validate-config "${mode}" 2>&1)"; then
        printf 'Expected valid %s configuration, got:\n%s\n' "${mode}" "${output}" >&2
        exit 1
    fi
}

assert_invalid() {
    mode="$1"
    expected="$2"
    if output="$(BIXI_ENV_FILE="${TEST_DIR}/runtime.env" \
            "${ROOT}/scripts/bixi.sh" validate-config "${mode}" 2>&1)"; then
        printf 'Expected invalid %s configuration to fail\n' "${mode}" >&2
        exit 1
    fi
    case "${output}" in
        *"${expected}"*) ;;
        *)
            printf 'Expected error containing %s, got:\n%s\n' "${expected}" "${output}" >&2
            exit 1
            ;;
    esac
}

write_env false false false
assert_valid single
assert_valid cloud

write_env true true false
assert_valid single
assert_invalid cloud 'BIXI_RELIABLE_RABBIT_ENABLED=true'

write_env true true true
assert_valid cloud

write_env true false false
assert_invalid single 'BIXI_RELIABLE_ENABLED=true'

write_env true false true
assert_invalid cloud 'BIXI_RELIABLE_ENABLED=true'

write_env false false false
printf 'OAUTH_BROWSER_CLIENT_SECRET=GENERATE_ON_FIRST_RUN\n' >>"${TEST_DIR}/runtime.env"
assert_invalid cloud 'OAUTH_BROWSER_CLIENT_SECRET'

write_env false false false
printf 'OAUTH_BROWSER_CLIENT_SECRET=abc123\n' >>"${TEST_DIR}/runtime.env"
assert_invalid single 'OAUTH_BROWSER_CLIENT_SECRET'

if output="$(OAUTH_BROWSER_CLIENT_SECRET="0123456789abcdef0123456789abcdef0123456789abcde'" \
        "${ROOT}/deploy/mysql/05_runtime_secrets.sh" 2>&1)"; then
    echo 'Database bootstrap must reject malformed browser OAuth secrets' >&2
    exit 1
fi
case "${output}" in
    *'OAUTH_BROWSER_CLIENT_SECRET'*) ;;
    *) echo 'Database bootstrap must identify the malformed browser OAuth secret' >&2; exit 1 ;;
esac

marker=/var/lib/mysql/.bixi-init-complete
grep -F 'MYSQL_PWD=$${MYSQL_ROOT_PASSWORD} mysqladmin ping -h 127.0.0.1 -uroot --silent' \
    "${ROOT}/compose.yaml" >/dev/null \
    || { echo 'MySQL health must avoid a password argument' >&2; exit 1; }
if grep -F "test -f ${marker}" "${ROOT}/compose.yaml" >/dev/null; then
    echo 'Development MySQL health must accept existing volumes' >&2
    exit 1
fi
grep -F "test -f ${marker} && MYSQL_PWD=\$\${MYSQL_ROOT_PASSWORD} mysqladmin ping" \
    "${ROOT}/compose.release.yaml" >/dev/null \
    || { echo 'Release MySQL health must require completed bootstrap' >&2; exit 1; }
grep -F "touch ${marker}" "${ROOT}/deploy/mysql/05_runtime_secrets.sh" >/dev/null \
    || { echo 'secret seeding must mark completed bootstrap' >&2; exit 1; }
mkdir "${TEST_DIR}/mysql-bin"
printf '%s\n' '#!/bin/sh' \
    'for arg do' \
    '    case "$arg" in *"$MYSQL_ROOT_PASSWORD"*) echo "root password leaked through mysql argv" >&2; exit 2 ;; esac' \
    'done' \
    '[ "${MYSQL_PWD:-}" = "$MYSQL_ROOT_PASSWORD" ] || { echo "mysql password was not passed through MYSQL_PWD" >&2; exit 2; }' \
    'cat >"$BIXI_TEST_SQL_LOG"' 'exit "${BIXI_TEST_MYSQL_EXIT:-0}"' \
    >"${TEST_DIR}/mysql-bin/mysql"
chmod +x "${TEST_DIR}/mysql-bin/mysql"
sed "s|${marker}|${TEST_DIR}/mysql-init-complete|g" \
    "${ROOT}/deploy/mysql/05_runtime_secrets.sh" >"${TEST_DIR}/mysql-seed.sh"
(
    export PATH="${TEST_DIR}/mysql-bin:${PATH}"
    export MYSQL_ROOT_PASSWORD=fixture MYSQL_DATABASE=bixi
    export BIXI_TEST_SQL_LOG="${TEST_DIR}/mysql.sql"
    export ADMIN_PASSWORD_BCRYPT_B64
    ADMIN_PASSWORD_BCRYPT_B64="$(printf 'fixture-hash' | base64 | tr -d '\n')"
    export OAUTH_INTERNAL_SEED="s';" OAUTH_PASSWORD_CLIENT_SECRET="a'b"
    export OAUTH_MOBILE_CLIENT_SECRET="m'--"
    export OAUTH_BROWSER_CLIENT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef
    if BIXI_TEST_MYSQL_EXIT=1 sh "${TEST_DIR}/mysql-seed.sh" >/dev/null 2>&1; then
        echo 'failed secret seeding was accepted' >&2
        exit 1
    fi
    [ ! -e "${TEST_DIR}/mysql-init-complete" ] \
        || { echo 'failed secret seeding wrote the completion marker' >&2; exit 1; }
    BIXI_TEST_MYSQL_EXIT=0 sh "${TEST_DIR}/mysql-seed.sh" >/dev/null
    [ -f "${TEST_DIR}/mysql-init-complete" ] \
        || { echo 'successful secret seeding did not write the completion marker' >&2; exit 1; }
    grep -F "UNHEX('73273b')" "$BIXI_TEST_SQL_LOG" >/dev/null
    grep -F "UNHEX('612762')" "$BIXI_TEST_SQL_LOG" >/dev/null
    grep -F "UNHEX('6d272d2d')" "$BIXI_TEST_SQL_LOG" >/dev/null
    if grep -F "a'b" "$BIXI_TEST_SQL_LOG" >/dev/null; then
        echo 'OAuth secret was interpolated as a SQL literal' >&2
        exit 1
    fi
)

generated_env="${TEST_DIR}/generated.env"
BIXI_ENV_FILE="${generated_env}" "${ROOT}/scripts/bixi.sh" init-env >/dev/null
grep -Eq '^OAUTH_BROWSER_CLIENT_SECRET=[0-9a-f]{48}$' "${generated_env}" \
    || { echo 'init-env must generate a random browser OAuth secret' >&2; exit 1; }

sed '/^OAUTH_BROWSER_CLIENT_SECRET=/d' "${generated_env}" >"${TEST_DIR}/upgraded.env"
chmod 644 "${TEST_DIR}/upgraded.env"
BIXI_ENV_FILE="${TEST_DIR}/upgraded.env" "${ROOT}/scripts/bixi.sh" init-env >/dev/null
grep -Eq '^OAUTH_BROWSER_CLIENT_SECRET=[0-9a-f]{48}$' "${TEST_DIR}/upgraded.env" \
    || { echo 'init-env must add the browser OAuth secret to existing environments' >&2; exit 1; }
[ "$(find "${TEST_DIR}/upgraded.env" -perm 0600 -print)" = "${TEST_DIR}/upgraded.env" ] \
    || { echo 'init-env must protect existing environments after adding secrets' >&2; exit 1; }

mkdir "${TEST_DIR}/no-openssl-bin" "${TEST_DIR}/no-random-bin" "${TEST_DIR}/no-base64-bin"
for tool in dirname cp awk mv mktemp rm; do
    ln -s "$(command -v "${tool}")" "${TEST_DIR}/no-openssl-bin/${tool}"
    ln -s "$(command -v "${tool}")" "${TEST_DIR}/no-random-bin/${tool}"
    ln -s "$(command -v "${tool}")" "${TEST_DIR}/no-base64-bin/${tool}"
done
for tool in od tr; do
    ln -s "$(command -v "${tool}")" "${TEST_DIR}/no-openssl-bin/${tool}"
    ln -s "$(command -v "${tool}")" "${TEST_DIR}/no-base64-bin/${tool}"
done
ln -s "$(command -v base64)" "${TEST_DIR}/no-openssl-bin/base64"
PATH="${TEST_DIR}/no-openssl-bin" BIXI_ENV_FILE="${TEST_DIR}/urandom.env" \
    "${ROOT}/scripts/bixi.sh" init-env >/dev/null
grep -Eq '^OAUTH_BROWSER_CLIENT_SECRET=[0-9a-f]{48}$' "${TEST_DIR}/urandom.env" \
    || { echo 'init-env must use /dev/urandom when OpenSSL is unavailable' >&2; exit 1; }
grep -Eq '^NACOS_AUTH_TOKEN=[A-Za-z0-9+/=]+$' "${TEST_DIR}/urandom.env" \
    || { echo 'init-env must encode the Nacos token when OpenSSL is unavailable' >&2; exit 1; }

for encoder_case in missing empty; do
    if [ "$encoder_case" = empty ]; then
        printf '#!/bin/sh\nexit 0\n' >"${TEST_DIR}/no-base64-bin/base64"
        chmod +x "${TEST_DIR}/no-base64-bin/base64"
    fi
    if output="$(PATH="${TEST_DIR}/no-base64-bin" BIXI_ENV_FILE="${TEST_DIR}/${encoder_case}-base64.env" \
            "${ROOT}/scripts/bixi.sh" init-env 2>&1)"; then
        echo "init-env accepted a ${encoder_case} base64 encoder" >&2
        exit 1
    fi
    case "$output" in
        *'NACOS_AUTH_TOKEN generation requires working base64'*) ;;
        *) echo "init-env did not explain the ${encoder_case} base64 failure" >&2; exit 1 ;;
    esac
    [ ! -e "${TEST_DIR}/${encoder_case}-base64.env" ] \
        || { echo "init-env left a partial environment after ${encoder_case} base64" >&2; exit 1; }
done

if output="$(PATH="${TEST_DIR}/no-random-bin" BIXI_ENV_FILE="${TEST_DIR}/no-random.env" \
        "${ROOT}/scripts/bixi.sh" init-env 2>&1)"; then
    echo 'init-env must reject missing secure random sources' >&2
    exit 1
fi
case "${output}" in
    *'secure random source'*) ;;
    *) echo 'init-env must explain the missing secure random source' >&2; exit 1 ;;
esac
[ ! -e "${TEST_DIR}/no-random.env" ] \
    || { echo 'init-env must not leave a partial environment after random generation fails' >&2; exit 1; }

# Start commands show the URL, while credentials prints the password on request.
# shellcheck disable=SC1090
. "${ROOT}/scripts/bixi.sh" >/dev/null

mkdir "${TEST_DIR}/hash-bin"
cat >"${TEST_DIR}/hash-bin/htpasswd" <<'HTPASSWD'
#!/bin/sh
[ "$#" -eq 3 ] && [ "$1" = -niBC ] && [ "$2" = 10 ] && [ -z "$3" ] \
    && [ "$(cat)" = "$BIXI_TEST_ADMIN_PASSWORD" ] || exit 1
printf ':%s\n' "$BIXI_TEST_HASH"
HTPASSWD
cat >"${TEST_DIR}/hash-bin/docker" <<'DOCKER'
#!/bin/sh
[ "$#" -eq 8 ] && [ "$1" = run ] && [ "$2" = --rm ] && [ "$3" = -i ] \
    && [ "$4" = httpd:2.4.62-alpine ] && [ "$5" = htpasswd ] \
    && [ "$6" = -niBC ] && [ "$7" = 10 ] && [ -z "$8" ] \
    && [ "$(cat)" = "$BIXI_TEST_ADMIN_PASSWORD" ] || exit 1
printf ':%s\n' "$BIXI_TEST_HASH"
DOCKER
chmod +x "${TEST_DIR}/hash-bin/htpasswd" "${TEST_DIR}/hash-bin/docker"
for hash_backend in local docker; do
    (
        ENV_FILE="${TEST_DIR}/${hash_backend}-hash.env"
        printf 'ADMIN_PASSWORD_BCRYPT_B64=GENERATE_ON_FIRST_RUN\n' >"$ENV_FILE"
        ADMIN_PASSWORD_BCRYPT_B64=GENERATE_ON_FIRST_RUN
        ADMIN_PASSWORD='fixture admin secret & ?'
        export BIXI_TEST_ADMIN_PASSWORD="$ADMIN_PASSWORD"
        export BIXI_TEST_HASH='$2y$10$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'
        PATH="${TEST_DIR}/hash-bin:${PATH}"
        export PATH
        load_env() { :; }
        require_docker() { :; }
        if [ "$hash_backend" = docker ]; then
            command_exists() { [ "$1" != htpasswd ]; }
        fi
        prepare_admin_hash >/dev/null
        hash_b64="$(sed -n 's/^ADMIN_PASSWORD_BCRYPT_B64=//p' "$ENV_FILE")"
        [ "$(printf '%s' "$hash_b64" | base64 -d)" = "$BIXI_TEST_HASH" ] \
            || { echo "$hash_backend administrator hash used an unsafe argument path" >&2; exit 1; }
    )
done

load_env() {
    BIXI_HTTP_PORT=8080
    ADMIN_USERNAME=test-admin
    ADMIN_PASSWORD=test-secret
}
access="$(show_access)"
case "${access}" in
    *'test-secret'*) echo 'start output must not print the administrator password' >&2; exit 1 ;;
esac
credentials="$(show_credentials)"
case "${credentials}" in
    *'test-secret'*) ;;
    *) echo 'credentials command must print the administrator password' >&2; exit 1 ;;
esac

printf 'Bixi runtime configuration contract tests passed.\n'
