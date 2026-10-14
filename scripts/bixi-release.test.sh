#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
REAL_DOCKER="$(command -v docker || true)"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "${TEST_DIR}"' EXIT HUP INT TERM
mkdir -p "${TEST_DIR}/bin" "${TEST_DIR}/tls"
printf 'certificate\n' >"${TEST_DIR}/tls/fullchain.pem"
printf 'key\n' >"${TEST_DIR}/tls/privkey.pem"

COMMIT=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
DIGEST=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
ADMIN_HASH="$(printf '%s' '$2y$12$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA' \
    | base64 | tr -d '\n')"
sed -e "s/REPLACE_WITH_FROZEN_40_HEX_COMMIT/${COMMIT}/" \
    -e "s/REPLACE_WITH_64_HEX_DIGEST/${DIGEST}/g" \
    "${ROOT}/.env.release.example" >"${TEST_DIR}/release.env"
sed -e 's/=GENERATE_ON_FIRST_RUN$/=fixture-secret/' \
    -e "s|^ADMIN_PASSWORD_BCRYPT_B64=.*|ADMIN_PASSWORD_BCRYPT_B64=${ADMIN_HASH}|" \
    -e 's/^BIXI_HTTPS_ENABLED=.*/BIXI_HTTPS_ENABLED=true/' \
    -e "s|^BIXI_TLS_DIR=.*|BIXI_TLS_DIR=${TEST_DIR}/tls|" \
    -e 's/^OAUTH_BROWSER_CLIENT_SECRET=.*/OAUTH_BROWSER_CLIENT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef/' \
    -e 's/^NACOS_CLIENT_PASSWORD=.*/NACOS_CLIENT_PASSWORD=fixture-client-password/' \
    "${ROOT}/.env.example" >"${TEST_DIR}/runtime.env"

cat >"${TEST_DIR}/bin/git" <<'GIT'
#!/bin/sh
case "$*" in
    *'rev-parse HEAD') printf '%s\n' "${BIXI_TEST_COMMIT}" ;;
    *'status --porcelain --untracked-files=normal') printf '%s' "${BIXI_TEST_GIT_STATUS:-}" ;;
    *) exit 1 ;;
esac
GIT
cat >"${TEST_DIR}/bin/docker" <<'DOCKER'
#!/bin/sh
printf '%s\n' "$*" >>"${BIXI_TEST_DOCKER_LOG}"
case "$*" in
    'compose version --short') printf '%s\n' "${BIXI_TEST_COMPOSE_VERSION:-5.5.0}" ;;
    volume\ ls*)
        if [ "${BIXI_TEST_VOLUME_EXISTS:-false}" = 'true' ]; then
            printf '%s\n' "${COMPOSE_PROJECT_NAME:-bixi}_mysql-data"
        fi ;;
    volume\ inspect*) printf '%s\n' "${BIXI_TEST_VOLUME_COMMIT:-<no value>}" ;;
esac
DOCKER
cat >"${TEST_DIR}/bin/curl" <<'CURL'
#!/bin/sh
exit 0
CURL
chmod +x "${TEST_DIR}/bin/git" "${TEST_DIR}/bin/docker" "${TEST_DIR}/bin/curl"

export PATH="${TEST_DIR}/bin:${PATH}"
export BIXI_ENV_FILE="${TEST_DIR}/runtime.env"
export BIXI_RELEASE_ENV_FILE="${TEST_DIR}/release.env"
export BIXI_TEST_DOCKER_LOG="${TEST_DIR}/docker.log"
export BIXI_TEST_COMMIT="${COMMIT}"

assert_reject() {
    expected="$1"
    : >"${BIXI_TEST_DOCKER_LOG}"
    if output="$("${ROOT}/scripts/bixi.sh" "${2:-start-single-release}" 2>&1)"; then
        printf 'Expected release preflight to reject %s\n' "${expected}" >&2
        exit 1
    fi
    case "${output}" in
        *"${expected}"*) ;;
        *) printf 'Expected %s, got:\n%s\n' "${expected}" "${output}" >&2; exit 1 ;;
    esac
    [ ! -s "${BIXI_TEST_DOCKER_LOG}" ] \
        || { echo 'Rejected release changed or queried Docker' >&2; exit 1; }
}

BIXI_TEST_GIT_STATUS=' M compose.yaml'
export BIXI_TEST_GIT_STATUS
assert_reject 'source tree is not frozen'
unset BIXI_TEST_GIT_STATUS

sed "s/^commit=.*/commit=cccccccccccccccccccccccccccccccccccccccc/" \
    "${TEST_DIR}/release.env" >"${TEST_DIR}/invalid.env"
BIXI_RELEASE_ENV_FILE="${TEST_DIR}/invalid.env" assert_reject 'frozen commit does not match HEAD'
BIXI_RELEASE_ENV_FILE="${TEST_DIR}/release.env"
export BIXI_RELEASE_ENV_FILE

sed "s|@sha256:${DIGEST}|:latest|g" \
    "${TEST_DIR}/release.env" >"${TEST_DIR}/invalid.env"
BIXI_RELEASE_ENV_FILE="${TEST_DIR}/invalid.env" assert_reject 'digest-pinned'
BIXI_RELEASE_ENV_FILE="${TEST_DIR}/release.env"
export BIXI_RELEASE_ENV_FILE

sed '/^BIXI_RELEASE_MYSQL_IMAGE=/d' "${TEST_DIR}/release.env" >"${TEST_DIR}/invalid.env"
BIXI_RELEASE_MYSQL_IMAGE="registry.example/foreign@sha256:${DIGEST}"
export BIXI_RELEASE_MYSQL_IMAGE
BIXI_RELEASE_ENV_FILE="${TEST_DIR}/invalid.env" assert_reject 'BIXI_RELEASE_MYSQL_IMAGE'
unset BIXI_RELEASE_MYSQL_IMAGE
BIXI_RELEASE_ENV_FILE="${TEST_DIR}/release.env"
export BIXI_RELEASE_ENV_FILE

sed 's/^BIXI_HTTPS_ENABLED=.*/BIXI_HTTPS_ENABLED=false/' \
    "${TEST_DIR}/runtime.env" >"${TEST_DIR}/invalid-runtime.env"
BIXI_ENV_FILE="${TEST_DIR}/invalid-runtime.env" assert_reject 'BIXI_HTTPS_ENABLED=true'
BIXI_ENV_FILE="${TEST_DIR}/runtime.env"
export BIXI_ENV_FILE

sed 's/^ADMIN_PASSWORD_BCRYPT_B64=.*/ADMIN_PASSWORD_BCRYPT_B64=GENERATE_ON_FIRST_RUN/' \
    "${TEST_DIR}/runtime.env" >"${TEST_DIR}/invalid-runtime.env"
BIXI_ENV_FILE="${TEST_DIR}/invalid-runtime.env" assert_reject 'precomputed ADMIN_PASSWORD_BCRYPT_B64'
BIXI_ENV_FILE="${TEST_DIR}/runtime.env"
export BIXI_ENV_FILE

sed 's/^MYSQL_PASSWORD=.*/MYSQL_PASSWORD=GENERATE_ON_FIRST_RUN/' \
    "${TEST_DIR}/runtime.env" >"${TEST_DIR}/invalid-runtime.env"
BIXI_ENV_FILE="${TEST_DIR}/invalid-runtime.env" assert_reject 'configured MYSQL_PASSWORD'
BIXI_ENV_FILE="${TEST_DIR}/runtime.env"
export BIXI_ENV_FILE

export BIXI_TEST_COMPOSE_VERSION=2.23.0
: >"${BIXI_TEST_DOCKER_LOG}"
if output="$("${ROOT}/scripts/bixi.sh" start-single-release 2>&1)"; then
    echo 'Release accepted Compose without !reset support' >&2
    exit 1
fi
case "${output}" in
    *'Compose 2.24+'*) ;;
    *) printf 'Expected Compose version failure, got:\n%s\n' "${output}" >&2; exit 1 ;;
esac
unset BIXI_TEST_COMPOSE_VERSION

: >"${BIXI_TEST_DOCKER_LOG}"
"${ROOT}/scripts/bixi.sh" start-single-release >/dev/null
grep -F -- "-f ${ROOT}/compose.release.yaml --profile single up --detach --no-build" \
    "${BIXI_TEST_DOCKER_LOG}" >/dev/null \
    || { echo 'Single release did not start with final release overlay and --no-build' >&2; exit 1; }
if grep -E '(^| )build( |$)|(^| )pull( |$)|image inspect' "${BIXI_TEST_DOCKER_LOG}" >/dev/null; then
    echo 'Release performed a development image preparation or build step' >&2
    exit 1
fi

: >"${BIXI_TEST_DOCKER_LOG}"
if output="$("${ROOT}/scripts/bixi.sh" start-cloud-release 2>&1)"; then
    echo 'Cloud release accepted the development Nacos namespace' >&2
    exit 1
fi
case "${output}" in
    *'NACOS_NAMESPACE=bixi-prod'*) ;;
    *) printf 'Expected production Nacos namespace failure, got:\n%s\n' "${output}" >&2; exit 1 ;;
esac
[ ! -s "${BIXI_TEST_DOCKER_LOG}" ] \
    || { echo 'Rejected cloud release changed or queried Docker' >&2; exit 1; }

sed 's/^NACOS_NAMESPACE=.*/NACOS_NAMESPACE=bixi-prod/' \
    "${TEST_DIR}/runtime.env" >"${TEST_DIR}/cloud.env"
: >"${BIXI_TEST_DOCKER_LOG}"
if output="$(BIXI_ENV_FILE="${TEST_DIR}/cloud.env" "${ROOT}/scripts/bixi.sh" start-cloud-release 2>&1)"; then
    echo 'Cloud release accepted missing Nacos authentication credentials' >&2
    exit 1
fi
case "${output}" in
    *'NACOS_AUTH_TOKEN'*) ;;
    *) printf 'Expected Nacos authentication preflight failure, got:\n%s\n' "${output}" >&2; exit 1 ;;
esac
if grep -E '(^| )(stop|up)( |$)' "${BIXI_TEST_DOCKER_LOG}" >/dev/null; then
    echo 'Cloud release changed containers before checking Nacos credentials' >&2
    exit 1
fi
printf '%s\n' \
    'NACOS_USERNAME=nacos' \
    'NACOS_PASSWORD=fixture-nacos-password' \
    'NACOS_CLIENT_PASSWORD=fixture-client-password' \
    'NACOS_AUTH_TOKEN=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=' \
    'NACOS_AUTH_IDENTITY_KEY=bixi-fixture' \
    'NACOS_AUTH_IDENTITY_VALUE=fixture-identity-value' >>"${TEST_DIR}/cloud.env"
sed 's/^NACOS_CLIENT_PASSWORD=.*/NACOS_CLIENT_PASSWORD=GENERATE_ON_FIRST_RUN/' \
    "${TEST_DIR}/cloud.env" >"${TEST_DIR}/invalid-runtime.env"
BIXI_ENV_FILE="${TEST_DIR}/invalid-runtime.env" \
    assert_reject 'configured NACOS_CLIENT_PASSWORD' start-cloud-release
sed 's/^NACOS_CLIENT_PASSWORD=.*/NACOS_CLIENT_PASSWORD=fixture-nacos-password/' \
    "${TEST_DIR}/cloud.env" >"${TEST_DIR}/invalid-runtime.env"
BIXI_ENV_FILE="${TEST_DIR}/invalid-runtime.env" \
    assert_reject 'NACOS_CLIENT_PASSWORD must differ from NACOS_PASSWORD' start-cloud-release
sed 's/^NACOS_AUTH_TOKEN=.*/NACOS_AUTH_TOKEN=================================================/' \
    "${TEST_DIR}/cloud.env" >"${TEST_DIR}/invalid-runtime.env"
BIXI_ENV_FILE="${TEST_DIR}/invalid-runtime.env" \
    assert_reject 'NACOS_AUTH_TOKEN must be base64 encoded' start-cloud-release
short_token="$(printf '%s' '0123456789012345678901234567890' | base64 | tr -d '\n')"
sed "s/^NACOS_AUTH_TOKEN=.*/NACOS_AUTH_TOKEN=${short_token}/" \
    "${TEST_DIR}/cloud.env" >"${TEST_DIR}/invalid-runtime.env"
BIXI_ENV_FILE="${TEST_DIR}/invalid-runtime.env" \
    assert_reject 'NACOS_AUTH_TOKEN must decode to at least 32 bytes' start-cloud-release
cat >"${TEST_DIR}/bin/lsof" <<'LSOF'
#!/bin/sh
case "$*" in *-iTCP:5001*) exit 0 ;; esac
exit 1
LSOF
chmod +x "${TEST_DIR}/bin/lsof"
: >"${BIXI_TEST_DOCKER_LOG}"
if output="$(BIXI_ENV_FILE="${TEST_DIR}/cloud.env" "${ROOT}/scripts/bixi.sh" start-cloud-release 2>&1)"; then
    echo 'Cloud release accepted an occupied monitor port' >&2
    exit 1
fi
case "${output}" in
    *'MONITOR_PORT port 5001 is already in use'*) ;;
    *) printf 'Expected monitor port failure, got:\n%s\n' "${output}" >&2; exit 1 ;;
esac
if grep -E '(^| )(stop|up)( |$)' "${BIXI_TEST_DOCKER_LOG}" >/dev/null; then
    echo 'Cloud release changed containers before checking the monitor port' >&2
    exit 1
fi
rm "${TEST_DIR}/bin/lsof"
: >"${BIXI_TEST_DOCKER_LOG}"
BIXI_ENV_FILE="${TEST_DIR}/cloud.env" "${ROOT}/scripts/bixi.sh" start-cloud-release >/dev/null
grep -F -- "-f ${ROOT}/compose.release.yaml --profile cloud up --detach --no-build" \
    "${BIXI_TEST_DOCKER_LOG}" >/dev/null \
    || { echo 'Cloud release did not start with final release overlay and --no-build' >&2; exit 1; }
if grep -E '(^| )build( |$)|(^| )pull( |$)|image inspect' "${BIXI_TEST_DOCKER_LOG}" >/dev/null; then
    echo 'Cloud release performed a development image preparation or build step' >&2
    exit 1
fi

export BIXI_TEST_VOLUME_EXISTS=true
for target in start-single-release start-cloud-release; do
    target_env="${TEST_DIR}/runtime.env"
    [ "${target}" = start-single-release ] || target_env="${TEST_DIR}/cloud.env"
    : >"${BIXI_TEST_DOCKER_LOG}"
    if output="$(BIXI_ENV_FILE="${target_env}" "${ROOT}/scripts/bixi.sh" "${target}" 2>&1)"; then
        printf 'Release accepted an existing development MySQL volume (%s)\n' "${target}" >&2
        exit 1
    fi
    case "${output}" in
        *'existing MySQL volume is not from frozen release'*) ;;
        *) printf 'Expected MySQL volume preflight failure, got:\n%s\n' "${output}" >&2; exit 1 ;;
    esac
    if grep -E '(^| )(stop|up|run|create|rm)( |$)' "${BIXI_TEST_DOCKER_LOG}" >/dev/null; then
        echo 'Rejected release changed containers or volumes' >&2
        exit 1
    fi
done
export BIXI_TEST_VOLUME_COMMIT="${COMMIT}"
for target in start-single-release start-cloud-release; do
    target_env="${TEST_DIR}/runtime.env"
    [ "${target}" = start-single-release ] || target_env="${TEST_DIR}/cloud.env"
    : >"${BIXI_TEST_DOCKER_LOG}"
    BIXI_ENV_FILE="${target_env}" "${ROOT}/scripts/bixi.sh" "${target}" >/dev/null
    grep -E '(^| )up --detach --no-build( |$)' "${BIXI_TEST_DOCKER_LOG}" >/dev/null \
        || { echo 'Same frozen release could not redeploy its MySQL volume' >&2; exit 1; }
done
unset BIXI_TEST_VOLUME_EXISTS BIXI_TEST_VOLUME_COMMIT

if [ -n "${REAL_DOCKER}" ] && "${REAL_DOCKER}" compose version >/dev/null 2>&1; then
    "${REAL_DOCKER}" compose --env-file "${TEST_DIR}/cloud.env" \
        -f "${ROOT}/compose.yaml" config --format json \
        | node -e '
            const assert = require("node:assert/strict");
            const fs = require("node:fs");
            const test = JSON.parse(fs.readFileSync(0, "utf8")).services.mysql.healthcheck.test[1];
            assert.equal(test, "MYSQL_PWD=$${MYSQL_ROOT_PASSWORD} mysqladmin ping -h 127.0.0.1 -uroot --silent");
        '
    "${REAL_DOCKER}" compose --env-file "${TEST_DIR}/cloud.env" \
        --env-file "${TEST_DIR}/release.env" \
        -f "${ROOT}/compose.yaml" -f "${ROOT}/compose.workflow-cluster.yaml" \
        -f "${ROOT}/compose.https.yaml" \
        -f "${ROOT}/compose.release.yaml" \
        --profile cloud --profile single --profile ai --profile workflow --profile workflow-cluster \
        config --format json \
        | node -e '
            const assert = require("node:assert/strict");
            const fs = require("node:fs");
            const config = JSON.parse(fs.readFileSync(0, "utf8"));
            const services = config.services;
            assert.equal(services.mysql.healthcheck.test[1], "test -f /var/lib/mysql/.bixi-init-complete && MYSQL_PWD=$${MYSQL_ROOT_PASSWORD} mysqladmin ping -h 127.0.0.1 -uroot --silent");
            assert.equal(config.volumes["mysql-data"].labels["com.bixi.release.commit"], "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            const mounts = services["nacos-config"].volumes;
            assert(mounts.some(volume => volume.target === "/config" && volume.source.endsWith("/deploy/nacos")));
            assert(mounts.some(volume => volume.target === "/prod-config" && volume.source.endsWith("/deploy/native/nacos")));
            assert.equal(services["nacos-config"].environment.NACOS_CONFIG_DIR, "/prod-config");
            assert.equal(services.nacos.environment.NACOS_AUTH_ENABLE, "true");
            assert.equal(services.nacos.environment.NACOS_AUTH_TOKEN, "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
            assert.equal(services["nacos-config"].environment.NACOS_USERNAME, "nacos");
            assert.equal(services["nacos-config"].environment.NACOS_PASSWORD, "fixture-nacos-password");
            assert.equal(services["nacos-config"].environment.NACOS_CLIENT_PASSWORD, "fixture-client-password");
            for (const name of ["monitor", "gateway", "auth", "upms", "generator", "quartz", "ai", "workflow", "workflow-a", "workflow-b"]) {
                assert.equal(services[name].environment.NACOS_USERNAME, "bixi-prod-client", name);
                assert.equal(services[name].environment.NACOS_PASSWORD, "fixture-client-password", name);
                assert.equal(services[name].environment.NACOS_CLIENT_PASSWORD, undefined, name);
            }
            for (const name of ["single", "upms", "generator", "quartz", "ai", "workflow"]) {
                assert.match(services[name].environment.MYSQL_JDBC_URL || "", /^jdbc:mysql:\/\/mysql:3306\//, name);
            }
        '
fi

printf 'Bixi release entry contract tests passed.\n'
